package zhttp.http

object HttpApp {

  /**
   * Creates an HTTP app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = Http.succeed(Response.text(str))

  /**
   * Creates an HTTP app which always responds with the same value.
   */
  def response[R, E](response: Response[R, E]): HttpApp[R, E] = Http.succeed(response)

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def empty(code: Status): HttpApp[Any, Nothing] = Http.succeed(Response.http(code))

  /**
   * Creates an HTTP app which always fails with the same error type.
   */
  def error(error: HttpError): HttpApp[Any, HttpError] = Http.fail(error)

  /**
   * Creates an HTTP app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] =
    Http.fromFunction[Request](req => Http.fail(HttpError.NotFound(req.url.path))).flatten

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = empty(Status.OK)

  /**
   * Creates an HTTP app that responds with 403 - Forbidden status code
   */
  def forbidden(msg: String): UHttpApp = Http.succeed(HttpError.Forbidden(msg).toResponse)

  /**
   * Creates a Http app from a function from Request to HttpApp
   */
  def fromFunction[R, E, B](f: Request => HttpApp[R, E]) = Http.fromFunction[Request](f)

}
