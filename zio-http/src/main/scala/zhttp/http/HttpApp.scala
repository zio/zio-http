package zhttp.http

import zhttp.socket.{Socket, WebSocketFrame}
import zio.ZIO

object HttpApp {

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
  def empty(code: Status): HttpApp[Any, Nothing] = HttpApp.response(Response.http(code))

  /**
   * Creates an HTTP app which always fails with the same error type.
   */
  def error(error: HttpError): HttpApp[Any, HttpError] = Http.fail(error)

  /**
   * Creates an HTTP app that fails with a [[NotFound]] exception.
   */
  def notFound: HttpApp[Any, HttpError] = Http.fromEffectFunction(req => ZIO.fail(HttpError.NotFound(req.url.path)))

  /**
   * Creates an HTTP app which always responds with a 200 status code.
   */
  def ok: HttpApp[Any, Nothing] = HttpApp.empty(Status.OK)

  /**
   * Creates an HTTP app which accepts a requests and produces a response effectfully.
   */
  def forsome[R, E >: HttpError](pf: PartialFunction[Request, ZIO[R, E, Response]]): HttpApp[R, E] =
    Http.identity[Request].collectM(pf)

  private val getRoute: Http[Any, Nothing, Request, (Method, Path)] = Http.identity[Request].map(_.route)

  /**
   * Creates an HTTP app which accepts a route and produces a response effectfully.
   */
  def routeM[R, E >: HttpError](pf: PartialFunction[Route, ZIO[R, E, Response]]): HttpApp[R, E] = {
    getRoute.collectM(pf)
  }

  /**
   * Creates an HTTP app which accepts a requests and produces a response.
   */
  def route[R, E >: HttpError](pf: PartialFunction[Route, Response]): HttpApp[R, E] = {
    getRoute.collect(pf)
  }

  /**
   * Creates an HTTP app which accepts a requests and produces another Http app as response.
   */
  def collectM[R, E >: HttpError](pf: PartialFunction[Route, HttpApp[R, E]]): HttpApp[R, E] =
    getRoute.collect(pf).flatten

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response.
   */
  def socket[R](pf: PartialFunction[Route, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]): HttpApp[R, HttpError] =
    getRoute.collect(pf).mapM(_.asResponse(None))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: HttpError](subProtocol: Option[String])(
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    getRoute.collect(pf).mapM(_.flatMap(_.asResponse(subProtocol)))

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response for the provided sub-protocol,
   * effectfully.
   */
  def socketM[R, E >: HttpError](subProtocol: String)(
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option(subProtocol))(pf)

  /**
   * Creates an HTTP app which accepts a requests and produces a websocket response effectfully.
   */
  def socketM[R, E >: HttpError](
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option.empty)(pf)
}
