package zhttp.http

import zhttp.socket.{IsResponse, Socket, WebSocketFrame}
import zio.ZIO

trait HttpConstructors {
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[A]: Http.MakeFromEffectFunction[A] = Http.MakeFromEffectFunction(())

  /**
   * Converts a ZIO to an Http type
   */
  def fromEffect[R, E, B](effect: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => effect)

  def fail[E](e: E): Http[Any, E, Any, Nothing] = Http.Fail(e)

  def identity[A]: Http[Any, Nothing, A, A] = Http.Identity

  def collect[A]: Http.MakeCollect[A] = Http.MakeCollect(())

  /**
   * Creates an HTTP app which accepts a requests and produces another Http app as response.
   */
  def collectM[A]: Http.MakeCollectM[A] = Http.MakeCollectM(())

  def succeedM[R, E, B](zio: ZIO[R, E, B]): Http[R, E, Any, B] = Http.fromEffectFunction(_ => zio)

  /**
   * Creates an HTTP app which always responds with the same plain text.
   */
  def text(str: String): HttpApp[Any, Nothing] = Http.succeed(Response.text(str))

  /**
   * Creates an HTTP app which always responds with the same value.
   */
  def response[R](response: Response): HttpApp[R, Nothing] = Http.succeed(response)

  /**
   * Creates an HTTP app which always responds with the same status code and empty data.
   */
  def empty(code: Status): HttpApp[Any, Nothing] = Http.response(Response.http(code))

  /**
   * Creates an HTTP app which always fails with the same error type.
   */
  def error(error: HttpError): HttpApp[Any, HttpError] = Http.fail(error)

  /**
   * Creates an HTTP app that fails with a NotFound exception.
   */
  def notFound: HttpApp[Any, HttpError] = Http.fromEffectFunction(req => ZIO.fail(HttpError.NotFound(req.url.path)))

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = Http.empty(Status.OK)

  /**
   * Creates an HTTP app which accepts a requests and produces a response effectfully.
   */
  def forsome[R, E](pf: PartialFunction[Request, ZIO[R, E, Response]])(implicit
    ev: CanSupportPartial[Request, E],
  ): HttpApp[R, E] =
    Http.identity[Request].collectM(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response.
   */
  def socket[R, E](
    pf: PartialFunction[Request, Socket[R, E, WebSocketFrame, WebSocketFrame]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
    error: CanSupportPartial[Request, E],
  ): HttpApp[R, E] =
    Http.collect(pf).mapM(_.asResponse(None))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E](subProtocol: Option[String])(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
    error: CanSupportPartial[Request, E],
  ): HttpApp[R, E] =
    Http.collect(pf).mapM(_.flatMap(_.asResponse(subProtocol)))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: HttpError](subProtocol: String)(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
    error: CanSupportPartial[Request, E],
  ): HttpApp[R, E] =
    socketM[R, E](Option(subProtocol))(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response effectfully.
   */
  def socketM[R, E >: HttpError](
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
    error: CanSupportPartial[Request, E],
  ): HttpApp[R, E] =
    socketM[R, E](Option.empty)(pf)

  /**
   * Flattens an Http app of an Http app
   */
  def flatten[R, E, A, B](http: Http[R, E, A, Http[R, E, A, B]]): Http[R, E, A, B] =
    http.flatten

  def flattenM[R, E, A, B](http: Http[R, E, A, ZIO[R, E, B]]): Http[R, E, A, B] =
    http.flatMap(Http.fromEffect)
}
