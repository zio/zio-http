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
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    HttpApp(
      Http
        .fromFunction[Request](req => Http.succeed(Response.fromHttpError(HttpError.NotFound(req.url.path))))
        .flatten,
    )

  /**
   * Creates an Http app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): HttpApp[Any, Nothing] =
    HttpApp(Http.succeed(Response.fromHttpError(HttpError.Forbidden(msg))))

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
