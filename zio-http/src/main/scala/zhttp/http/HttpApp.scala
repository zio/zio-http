package zhttp.http

import io.netty.channel._
import zhttp.service.{Handler, HttpRuntime}
import zio._

case class HttpApp[-R, +E](asHttp: Http[R, E, Request, Response[R, E]]) { self =>
  def orElse[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] =
    HttpApp(self.asHttp orElse other.asHttp)

  def defaultWith[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] =
    HttpApp(self.asHttp defaultWith other.asHttp)

  def <>[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] = self orElse other

  def +++[R1 <: R, E1 >: E](other: HttpApp[R1, E1]): HttpApp[R1, E1] = self defaultWith other

  /**
   * Converts a failing Http app into a non-failing one by handling the failure and converting it to a result if
   * possible.
   */
  def silent[R1 <: R, E1 >: E](implicit s: CanBeSilenced[E1, Response[R1, E1]]): HttpApp[R1, E1] =
    self.catchAll(e => Http.succeed(s.silent(e)).toApp)

  /**
   * Combines multiple Http apps into one
   */
  def combine[R1 <: R, E1 >: E](i: Iterable[HttpApp[R1, E1]]): HttpApp[R1, E1] =
    i.reduce(_.defaultWith(_))

  /**
   * Catches all the exceptions that the http app can fail with
   */
  def catchAll[R1 <: R, E1](f: E => HttpApp[R1, E1])(implicit ev: CanFail[E]): HttpApp[R1, E1] =
    HttpApp(self.asHttp.catchAll(f(_).asHttp).asInstanceOf[Http[R1, E1, Request, Response[R1, E1]]])

  private[zhttp] def compile[R1 <: R](zExec: HttpRuntime[R1])(implicit
    evE: E <:< Throwable,
  ): ChannelHandler =
    Handler(self, zExec)

  /**
   * Provides the environment to Http.
   */
  def provide(r: R)(implicit ev: NeedsEnv[R]) = self.asHttp.provide(r)

  /**
   * Provides some of the environment to Http.
   */
  def provideSome[R1 <: R](r: R1 => R)(implicit ev: NeedsEnv[R]) = self.asHttp.provideSome(r)

  /**
   * Provides layer to Http.
   */
  def provideLayer[R0, R1, E1 >: E](layer: ZLayer[R0, E1, R1])(implicit
    ev1: R1 <:< R,
    ev2: NeedsEnv[R],
  ) = self.asHttp.provideLayer(layer)

  /**
   * Provide part of the environment to HTTP that is not part of ZEnv
   */
  def provideCustomLayer[E1 >: E, R1 <: Has[_]](layer: ZLayer[ZEnv, E1, R1])(implicit
    ev: ZEnv with R1 <:< R,
    tagged: Tag[R1],
  ) = self.asHttp.provideCustomLayer(layer)

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  def provideSomeLayer[R0 <: Has[_], R1 <: Has[_], E1 >: E](
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1]) = self.asHttp.provideSomeLayer(layer)
}

object HttpApp {

  final case class InvalidMessage(message: Any) extends IllegalArgumentException {
    override def getMessage: String = s"Endpoint could not handle message: ${message.getClass.getName}"
  }

  /**
   * Creates an Http app from an Http type
   */
  def fromHttp[R, E](http: Http[R, E, Request, Response[R, E]]): HttpApp[R, E] = HttpApp(http)

  /**
   * Creates an Http app which always fails with the same error.
   */
  def fail[E](cause: E): HttpApp[Any, E] = HttpApp(Http.fail(cause))

  /**
   * Creates an Http app which always responds with empty data.
   */
  def empty: HttpApp[Any, Nothing] = HttpApp(Http.empty)

  /**
   * Creates an Http app which accepts a request and produces response.
   */
  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.collect(pf))

  /**
   * Creates an Http app which accepts a requests and produces a ZIO as response.
   */
  def collectM[R, E](pf: PartialFunction[Request, ZIO[R, E, Response[R, E]]]): HttpApp[R, E] =
    HttpApp(Http.collectM(pf))

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.fromEffectFunction(f))

  /**
   * Converts a ZIO to an Http app type
   */
  def responseM[R, E](res: ZIO[R, E, Response[R, E]]): HttpApp[R, E] = HttpApp(Http.fromEffect(res))

  /**
   * Creates an Http app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = HttpApp(Http.succeed(Response.text(str)))

  /**
   * Creates an Http app which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpApp[R, E] = HttpApp(Http.succeed(response))

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def status(code: Status): HttpApp[Any, Nothing] = HttpApp(Http.succeed(Response(code)))

  /**
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    HttpApp(
      Http
        .fromFunction[Request](req => Http.succeed(Response.fromHttpError(HttpError.NotFound(req.url.path))))
        .flatten,
    )

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = status(Status.OK)

  /**
   * Creates an HTTP app which always responds with a 100 status code.
   */
  def continue: HttpApp[Any, Nothing] = status(Status.CONTINUE)

  /**
   * Creates an HTTP app which always responds with a 101 status code.
   */
  def switchingProtocols: HttpApp[Any, Nothing] = status(Status.SWITCHING_PROTOCOLS)

  /**
   * Creates an HTTP app which always responds with a 102 status code.
   */
  def processing: HttpApp[Any, Nothing] = status(Status.PROCESSING)

  /**
   * Creates an HTTP app which always responds with a 201 status code.
   */
  def created: HttpApp[Any, Nothing] = status(Status.CREATED)

  /**
   * Creates an HTTP app which always responds with a 202 status code.
   */
  def accepted: HttpApp[Any, Nothing] = status(Status.ACCEPTED)

  /**
   * Creates an HTTP app which always responds with a 203 status code.
   */
  def nonAuthoritativeInformation: HttpApp[Any, Nothing] = status(Status.NON_AUTHORITATIVE_INFORMATION)

  /**
   * Creates an HTTP app which always responds with a 204 status code.
   */
  def noContent: HttpApp[Any, Nothing] = status(Status.NO_CONTENT)

  /**
   * Creates an HTTP app which always responds with a 205 status code.
   */
  def resetContent: HttpApp[Any, Nothing] = status(Status.RESET_CONTENT)

  /**
   * Creates an HTTP app which always responds with a 206 status code.
   */
  def partialContent: HttpApp[Any, Nothing] = status(Status.PARTIAL_CONTENT)

  /**
   * Creates an HTTP app which always responds with a 207 status code.
   */
  def multiStatus: HttpApp[Any, Nothing] = status(Status.MULTI_STATUS)

  /**
   * Creates an HTTP app which always responds with a 300 status code.
   */
  def multipleChoices: HttpApp[Any, Nothing] = status(Status.MULTIPLE_CHOICES)

  /**
   * Creates an HTTP app which always responds with a 301 status code.
   */
  def movedPermanently: HttpApp[Any, Nothing] = status(Status.MOVED_PERMANENTLY)

  /**
   * Creates an HTTP app which always responds with a 302 status code.
   */
  def found: HttpApp[Any, Nothing] = status(Status.FOUND)

  /**
   * Creates an HTTP app which always responds with a 303 status code.
   */
  def seeOther: HttpApp[Any, Nothing] = status(Status.SEE_OTHER)

  /**
   * Creates an HTTP app which always responds with a 304 status code.
   */
  def notModified: HttpApp[Any, Nothing] = status(Status.NOT_MODIFIED)

  /**
   * Creates an HTTP app which always responds with a 305 status code.
   */
  def useProxy: HttpApp[Any, Nothing] = status(Status.USE_PROXY)

  /**
   * Creates an HTTP app which always responds with a 307 status code.
   */
  def temporaryRedirect: HttpApp[Any, Nothing] = status(Status.TEMPORARY_REDIRECT)

  /**
   * Creates an HTTP app which always responds with a 308 status code.
   */
  def permanentRedirect: HttpApp[Any, Nothing] = status(Status.PERMANENT_REDIRECT)

  /**
   * Creates an HTTP app which always responds with a 400 status code.
   */
  def badRequest(msg: String): HttpApp[Any, Nothing] = HttpApp(
    Http.succeed(Response.fromHttpError(HttpError.BadRequest(msg))),
  )

  /**
   * Creates an HTTP app which always responds with a 401 status code.
   */
  def unauthorized(msg: String): HttpApp[Any, Nothing] = HttpApp(
    Http.succeed(Response.fromHttpError(HttpError.Unauthorized(msg))),
  )

  /**
   * Creates an HTTP app which always responds with a 402 status code.
   */
  def paymentRequired(msg: String): HttpApp[Any, Nothing] = HttpApp(
    Http.succeed(Response.fromHttpError(HttpError.PaymentRequired(msg))),
  )

  /**
   * Creates an Http app that responds with 403 status code
   */
  def forbidden(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Forbidden(msg))))

  /**
   * Creates an HTTP app which always responds with a 404 status code.
   */
  def notFound(path: Path): HttpApp[Any, Nothing] = HttpApp(
    Http.succeed(Response.fromHttpError(HttpError.NotFound(path))),
  )

  /**
   * Creates an Http app that responds with 405 status code
   */
  def methodNotAllowed(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.MethodNotAllowed(msg))))

  /**
   * Creates an Http app that responds with 406 status code
   */
  def notAcceptable(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.NotAcceptable(msg))))

  /**
   * Creates an Http app that responds with 407 status code
   */
  def proxyAuthenticationRequired(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.ProxyAuthenticationRequired(msg))))

  /**
   * Creates an Http app that responds with 408 status code
   */
  def requestTimeout(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.RequestTimeout(msg))))

  /**
   * Creates an Http app that responds with 409 status code
   */
  def conflict(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Conflict(msg))))

  /**
   * Creates an Http app that responds with 410 status code
   */
  def gone(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Gone(msg))))

  /**
   * Creates an Http app that responds with 411 status code
   */
  def lengthRequired(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.LengthRequired(msg))))

  /**
   * Creates an Http app that responds with 412 status code
   */
  def preConditionFailed(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.PreconditionFailed(msg))))

  /**
   * Creates an Http app that responds with 413 status code
   */
  def requestEntityTooLarge(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.RequestEntityTooLarge(msg))))

  /**
   * Creates an Http app that responds with 414 status code
   */
  def requestUriTooLong(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.RequestUriTooLong(msg))))

  /**
   * Creates an Http app that responds with 415 status code
   */
  def unsupportedMediaType(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.UnsupportedMediaType(msg))))

  /**
   * Creates an Http app that responds with 416 status code
   */
  def requestedRangeNotSatisfiable(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.RequestedRangeNotSatisfiable(msg))))

  /**
   * Creates an Http app that responds with 417 status code
   */
  def expectationFailed(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.ExpectationFailed(msg))))

  /**
   * Creates an Http app that responds with 421 status code
   */
  def misdirectedRequest(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.MisdirectedRequest(msg))))

  /**
   * Creates an Http app that responds with 422 status code
   */
  def unprocessableEntity(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.UnprocessableEntity(msg))))

  /**
   * Creates an Http app that responds with 423 status code
   */
  def locked(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Locked(msg))))

  /**
   * Creates an Http app that responds with 424 status code
   */
  def failedDependency(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.FailedDependency(msg))))

  /**
   * Creates an Http app that responds with 425 status code
   */
  def unorderedCollection(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.UnorderedCollection(msg))))

  /**
   * Creates an Http app that responds with 426 status code
   */
  def upgradeRequired(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.UpgradeRequired(msg))))

  /**
   * Creates an Http app that responds with 428 status code
   */
  def PreconditionRequired(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.PreconditionRequired(msg))))

  /**
   * Creates an Http app that responds with 429 status code
   */
  def TooManyRequests(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.TooManyRequests(msg))))

  /**
   * Creates an Http app that responds with 431 status code
   */
  def requestHeaderFieldsTooLarge(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.RequestHeaderFieldsTooLarge(msg))))

  /**
   * Creates an Http app that responds with 500 status code
   */
  def InternalServerError(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.InternalServerError(msg))))

  /**
   * Creates an Http app that responds with 501 status code
   */
  def notImplemented(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.NotImplemented(msg))))

  /**
   * Creates an Http app that responds with 502 status code
   */
  def badGateway(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.BadGateway(msg))))

  /**
   * Creates an Http app that responds with 503 status code
   */
  def serviceUnavailable(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.ServiceUnavailable(msg))))

  /**
   * Creates an Http app that responds with 504 status code
   */
  def gatewayTimeout(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.GatewayTimeout(msg))))

  /**
   * Creates an Http app that responds with 505 status code
   */
  def httpVersionNotSupported(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.HttpVersionNotSupported(msg))))

  /**
   * Creates an Http app that responds with 506 status code
   */
  def variantAlsoNegotiates(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.VariantAlsoNegotiates(msg))))

  /**
   * Creates an Http app that responds with 507 status code
   */
  def insufficientStorage(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.InsufficientStorage(msg))))

  /**
   * Creates an Http app that responds with 510 status code
   */
  def notExtended(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.NotExtended(msg))))

  /**
   * Creates an Http app that responds with 511 status code
   */
  def networkAuthenticationRequired(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.NetworkAuthenticationRequired(msg))))

  /**
   * Creates a Http app from a function from Request to HttpApp
   */
  def fromFunction[R, E, B](f: Request => HttpApp[R, E]): HttpApp[R, E] =
    HttpApp(Http.fromFunction[Request](f(_).asHttp).flatten)

  /**
   * Creates a Http app from a partial function from Request to HttpApp
   */
  def fromPartialFunction[R, E, A, B](f: Request => ZIO[R, Option[E], Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.fromPartialFunction(f))
}
