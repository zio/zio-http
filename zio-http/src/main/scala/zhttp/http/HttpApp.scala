package zhttp.http

import zhttp.socket.{IsResponse, Socket, WebSocketFrame}
import zio.ZIO

object HttpApp {

  /**
   * Creates an Http app from a function that returns a ZIO
   */
  def fromEffectFunction[R, E](f: Request => ZIO[R, E, Response[R, E]]): HttpApp[R, E] = Http.fromEffectFunction(f)

  /**
   * Converts a ZIO to an Http type
   */
  def responseM[R, E](res: ResponseM[R, E]): HttpApp[R, E] = Http.fromEffect(res)

  def fail[E](e: E): HttpApp[Any, E] = Http.fail(e)

  def collect[R, E: PartialRequest](pf: PartialFunction[Request, Response[R, E]]): HttpApp[R, E] =
    Http.collect[Request](pf)

  /**
   * Creates an HTTP app which accepts a requests and produces another Http app as response.
   */
  def collectM[R, E >: Throwable](pf: PartialFunction[Request, ResponseM[R, E]]): HttpApp[R, E] =
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
  def empty(code: Status): HttpApp[Any, Nothing] = Http.succeed(Response.http(code))

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
  def ok: HttpApp[Any, Nothing] = HttpApp.empty(Status.OK)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response.
   */
  def socket[R, E >: Throwable](
    pf: PartialFunction[Request, Socket[R, E, WebSocketFrame, WebSocketFrame]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
  ): HttpApp[R, E] =
    Http.collect(pf).mapM(_.asResponse(None))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: Throwable](subProtocol: Option[String])(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
  ): HttpApp[R, E] =
    Http.collect(pf).mapM(_.flatMap(_.asResponse(subProtocol)))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: Throwable](subProtocol: String)(
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
  ): HttpApp[R, E] =
    socketM[R, E](Option(subProtocol))(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response effectfully.
   */
  def socketM[R, E >: Throwable](
    pf: PartialFunction[Request, ZIO[R, E, Socket[R, E, WebSocketFrame, WebSocketFrame]]],
  )(implicit
    ev: IsResponse[R, E, WebSocketFrame, WebSocketFrame],
  ): HttpApp[R, E] =
    socketM[R, E](Option.empty)(pf)
}
