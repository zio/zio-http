package zhttp.http

import zio.ZIO

object HttpApp {

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    new HttpApp(Http.fromEffectFunction(f))

  /**
   * Converts a ZIO to an Http type
   */
  def responseM[R, E](res: ResponseM[R, E]): HttpApp[R, E] = new HttpApp(Http.fromEffect(res))

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    new HttpApp(Http.collect[Request](pf))

  /**
   * Creates an HTTP app which accepts a requests and produces another Http app as response.
   */
  def collectM[R, E](pf: PartialFunction[Request, ResponseM[R, E]]): HttpApp[R, E] =
    new HttpApp(Http.collectM[Request](pf))

  /**
   * Creates an HTTP app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = new HttpApp(Http.succeed(Response.text(str)))

  /**
   * Creates an HTTP app which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpApp[R, E] = new HttpApp(Http.succeed(response))

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def empty(code: Status): HttpApp[Any, Nothing] = new HttpApp(Http.succeed(Response.http(code)))

  /**
   * Creates an HTTP app which always fails with the same error type.
   */
  def error(error: HttpError): HttpApp[Any, HttpError] = new HttpApp(Http.fail(error))

  /**
   * Creates an HTTP app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    new HttpApp(Http.fromFunction[Request](req => Http.fail(HttpError.NotFound(req.url.path))).flatten)

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = empty(Status.OK)

  /**
   * Creates an HTTP app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): UHttpApp = new HttpApp(Http.succeed(HttpError.Forbidden(msg).toResponse))

  final class HttpApp[-R, +E](val asHttp: Http[R, E, Request, Response[R, E]]) extends AnyVal
}
