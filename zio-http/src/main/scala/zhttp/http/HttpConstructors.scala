package zhttp.http

import zhttp.socket.{Socket, WebSocketFrame}
import zio.ZIO

trait HttpConstructors {
  def succeed[B](b: B): Http[Any, Nothing, Any, B] = Http.Succeed(b)

  def fromEffectFunction[A]: Http.MakeFromEffectFunction[A] = Http.MakeFromEffectFunction(())

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
  def forsome[R, E >: HttpError](pf: PartialFunction[Request, ZIO[R, E, Response]]): HttpApp[R, E] =
    Http.identity[Request].collectM(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response.
   */
  def socket[R](
    pf: PartialFunction[Request, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]],
  ): HttpApp[R, HttpError] = Http.collect(pf).mapM(_.asResponse(None))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: HttpError](subProtocol: Option[String])(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    Http.collect(pf).mapM(_.flatMap(_.asResponse(subProtocol)))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: HttpError](subProtocol: String)(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option(subProtocol))(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response effectfully.
   */
  def socketM[R, E >: HttpError](
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option.empty)(pf)
}
