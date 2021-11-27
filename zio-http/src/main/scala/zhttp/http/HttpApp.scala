package zhttp.http

import zio._

import java.nio.charset.Charset

object HttpApp {

  /**
   * Creates an HTTP app which always responds with a 400 status code.
   */
  def badRequest(msg: String): HttpApp[Any, Nothing] = HttpApp.error(HttpError.BadRequest(msg))

  /**
   * Creates an Http app which accepts a request and produces response.
   */
  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    Http.collect(pf)

  /**
   * Creates an Http app which accepts a requests and produces a ZIO as response.
   */
  def collectM[R, E](pf: PartialFunction[Request, ZIO[R, E, Response[R, E]]]): HttpApp[R, E] =
    Http.collectM(pf)

  /**
   * Creates an Http app which always responds the provided data and a 200 status code
   */
  def data[R, E](data: HttpData[R, E]): HttpApp[R, E] = response(Response(data = data))

  /**
   * Creates an Http app which always responds with empty data.
   */
  def empty: HttpApp[Any, Nothing] = Http.empty

  /**
   * Creates an HTTP app with HttpError.
   */
  def error(cause: HttpError): HttpApp[Any, Nothing] = HttpApp.response(Response.fromHttpError(cause))

  /**
   * Creates an Http app that responds with 500 status code
   */
  def error(msg: String): HttpApp[Any, Nothing] = HttpApp.error(HttpError.InternalServerError(msg))

  /**
   * Creates an Http app which always fails with the same error.
   */
  def fail[E](cause: E): HttpApp[Any, E] = Http.fail(cause)

  /**
   * Creates an Http app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): HttpApp[Any, Nothing] = HttpApp.error(HttpError.Forbidden(msg))

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    Http.fromEffectFunction(f)

  /**
   * Creates a Http app from a function from Request to HttpApp
   */
  def fromFunction[R, E, B](f: Request => HttpApp[R, E]): HttpApp[R, E] =
    Http.fromFunction[Request](f(_)).flatten

  /**
   * Creates a Http app from a function from Request to `ZIO[R,E,HttpApp[R,E]]`
   */
  def fromFunctionM[R, E, B](f: Request => ZIO[R, E, HttpApp[R, E]]): HttpApp[R, E] =
    Http.fromFunctionM[Request](f(_)).flatten

  /**
   * Creates a Http app from a partial function from Request to HttpApp
   */
  def fromOptionFunction[R, E, A, B](f: Request => ZIO[R, Option[E], Response[R, E]]): HttpApp[R, E] =
    Http.fromPartialFunction(f)

  /**
   * Creates an Http app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, Nothing] = HttpApp.fromFunction(req => HttpApp.error(HttpError.NotFound(req.url.path)))

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = status(Status.OK)

  /**
   * Creates an Http app which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpApp[R, E] = Http.succeed(response)

  /**
   * Converts a ZIO to an Http app type
   */
  def responseM[R, E](res: ZIO[R, E, Response[R, E]]): HttpApp[R, E] = Http.fromEffect(res)

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def status(code: Status): HttpApp[Any, Nothing] = Http.succeed(Response(code))

  /**
   * Creates an Http app which always responds with the same plain text.
   */
  def text(str: String, charset: Charset = HTTP_CHARSET): HttpApp[Any, Nothing] =
    Http.succeed(Response.text(str, charset))

  final case class InvalidMessage(message: Any) extends IllegalArgumentException {
    override def getMessage: String = s"Endpoint could not handle message: ${message.getClass.getName}"
  }

}
