package zhttp.http

import zio.ZIO

final case class HttpApp[-R, +E](asHttp: Http[R, E, Request, Response[R, E]]) extends AnyVal {

  /**
   * Converts a failing Http into a non-failing one by handling the failure and converting it to a result if possible.
   */
  def silent[R1 <: R, E1 >: E](implicit s: CanBeSilenced[E1, Response[R1, E1]]) = HttpApp(
    asHttp.catchAll(e => Http.succeed(s.silent(e))),
  )

  /**
   * Combines two HttpApps into one.
   */
  def +++[R1 <: R, E1 >: E](other: HttpApp[R1, E1]) = HttpApp(asHttp +++ other.asHttp)

  /**
   * Evaluates the app and returns an HttpResult that can be resolved further
   */
  def execute(r: Request) = asHttp.execute(r)
}

object HttpApp {

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request[Any, Nothing, Nothing] => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    HttpApp(Http.fromEffectFunction(f))

  /**
   * Converts a ZIO to an Http type
   */
  def responseM[R, E](res: ResponseM[R, E]): HttpApp[R, E] = HttpApp(Http.fromEffect(res))

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[R, E, B](
    pf: PartialFunction[Request[Any, Nothing, Nothing], Response[R, E, B]],
  ): HttpApp[R, E] =
    Http.collect[Request[Any, Nothing, Nothing]](pf)

  def collectComplete[R, E, B: HasContent](
    pf: PartialFunction[Request[Any, Nothing, Complete], Response[R, E, B]],
  ): HttpApp[R, E] =
    Http.collect[Request[Any, Nothing, Complete]] { req =>
      Response.decodeComplete { bytes =>
        pf(req.copy(content = Content.fromBytes(bytes)))
      }
    }
  def collectBuffered[R, E, B: HasContent](
    pf: PartialFunction[Request[Any, Nothing, Buffered], Response[R, E, B]],
  ): HttpApp[R, E] =
    Http.collect[Request[Any, Nothing, Buffered]] { req =>
      Response.decodeBuffered { stream =>
        pf(req.copy(content = Content.fromStream(stream)))
      }

    }

  def collectM[R, E, B](
    pf: PartialFunction[Request[Any, Nothing, Nothing], ResponseM[R, E, B]],
  ): HttpApp[R, E] =
    HttpApp(Http.collectM[Request[Any, Nothing, Nothing]](pf))

  /**
   * Creates an HTTP app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = HttpApp(Http.succeed(Response.text(str)))

  /**
   * Creates an HTTP app which always responds with the same value.
   */
  def response[R, E, B: HasContent](response: Response[R, E, B]): HttpApp[R, E] = HttpApp(Http.succeed(response))

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def empty(code: Status): HttpApp[Any, Nothing] = HttpApp(Http.succeed(Response.http(code)))

  /**
   * Creates an HTTP app which always fails with the same error type.
   */
  def error(error: HttpError): HttpApp[Any, HttpError] = HttpApp(Http.fail(error))

  /**
   * Creates an HTTP app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    HttpApp(
      Http.fromFunction[Request[Any, Nothing, Nothing]](req => Http.fail(HttpError.NotFound(req.url.path))).flatten,
    )

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = empty(Status.OK)

  /**
   * Creates an HTTP app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): UHttpApp = HttpApp(Http.succeed(HttpError.Forbidden(msg).toResponse))

  /**
   * Creates a Http app from a function from Request to HttpApp
   */
  def fromFunction[R, E, B](f: Request => HttpApp[R, E]) = HttpApp(Http.flatten(Http.fromFunction(f).map(_.asHttp)))

}
