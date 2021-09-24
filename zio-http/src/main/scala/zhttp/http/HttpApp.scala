package zhttp.http

import zio._

object HttpApp {
  def apply[R, E](self: HttpApp[R, E]): HttpApp[R, E] = self

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] =
    Http.fromEffectFunction(f)

  /**
   * Converts a ZIO to an Http type
   */
  def responseM[R, E](res: ResponseM[R, E]): HttpApp[R, E] = Http.fromEffect(res)

  /**
   * Creates an HTTP app which accepts a request and produces response.
   */
  def collect[R, E](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    Http.collect[Request](pf)

  /**
   * Creates an HTTP app which accepts a requests and produces another Http app as response.
   */
  def collectM[R, E](pf: PartialFunction[Request, ResponseM[R, E]]): HttpApp[R, E] =
    Http.collectM[Request](pf)

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
  def empty(code: Status): HttpApp[Any, Nothing] = Http.succeed(Response(code))

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

  /**
   * Provides the environment to Http.
   */
  def provide[R, E](http: HttpApp[R, E], r: R)(implicit ev: NeedsEnv[R]) = http.provide(r)

  /**
   * Provides some of the environment to Http.
   */
  def provideSome[R, E, R1 <: R](http: HttpApp[R, E], r: R1 => R)(implicit ev: NeedsEnv[R]) = http.provideSome(r)

  /**
   * Provides layer to Http.
   */
  def provideLayer[R, R0, R1, E, E1 >: E](http: HttpApp[R, E], layer: ZLayer[R0, E1, R1])(implicit
    ev1: R1 <:< R,
    ev2: NeedsEnv[R],
  ) = http.provideLayer(layer)

  /**
   * Provide part of the environment to HTTP that is not part of ZEnv
   */
  def provideCustomLayer[E, E1 >: E, R, R1 <: Has[_]](http: HttpApp[R, E], layer: ZLayer[ZEnv, E1, R1])(implicit
    ev: ZEnv with R1 <:< R,
    tagged: Tag[R1],
  ) = http.provideCustomLayer(layer)

  /**
   * Provides some of the environment to Http leaving the remainder `R0`.
   */
  def provideSomeLayer[R, R0 <: Has[_], R1 <: Has[_], E, E1 >: E](
    http: HttpApp[R, E],
    layer: ZLayer[R0, E1, R1],
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R1]) = http.provideSomeLayer(layer)

}
