package zio-http.domain.http

import zio-http.domain.http.model._
import zio-http.domain.socket.{Socket, WebSocketFrame}
import zio.ZIO

object HttpApp {
  def text(str: String): HttpApp[Any, Nothing] = Http.succeed(Response.text(str))

  def response[R](response: Response): HttpApp[R, Nothing] = Http.succeed(response)

  def empty(code: Status): HttpApp[Any, Nothing] = HttpApp.response(Response.http(code))

  def error(error: HttpError): HttpApp[Any, HttpError] = Http.fail(error)

  def notFound: HttpApp[Any, HttpError] = Http.fromEffectFunction(req => ZIO.fail(HttpError.NotFound(req.url.path)))

  def ok: HttpApp[Any, Nothing] = HttpApp.empty(Status.OK)

  def forsome[R, E >: HttpError](pf: PartialFunction[Request, ZIO[R, E, Response]]): HttpApp[R, E] =
    Http.identity[Request].collectM(pf)

  private val getRoute: Http[Any, Nothing, Request, (Method, Path)] = Http.identity[Request].map(_.route)

  def routeM[R, E >: HttpError](pf: PartialFunction[Route, ZIO[R, E, Response]]): HttpApp[R, E] = {
    getRoute.collectM(pf)
  }

  def route[R, E >: HttpError](pf: PartialFunction[Route, Response]): HttpApp[R, E] = {
    getRoute.collect(pf)
  }

  def collectM[R, E >: HttpError](pf: PartialFunction[Route, HttpApp[R, E]]): HttpApp[R, E] =
    getRoute.collect(pf).flatten

  def socket[R](pf: PartialFunction[Route, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]): HttpApp[R, HttpError] =
    getRoute.collect(pf).mapM(_.asResponse(None))

  def socketM[R, E >: HttpError](subProtocol: Option[String])(
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    getRoute.collect(pf).mapM(_.flatMap(_.asResponse(subProtocol)))

  def socketM[R, E >: HttpError](subProtocol: String)(
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option(subProtocol))(pf)

  def socketM[R, E >: HttpError](
    pf: PartialFunction[Route, ZIO[R, E, Socket[R, Nothing, WebSocketFrame, WebSocketFrame]]],
  ): HttpApp[R, E] =
    socketM[R, E](Option.empty)(pf)

  /**
   * Attaches content length header wherever possible.
   */
  def setContentLength[R, E](http: HttpApp[R, E]): HttpApp[R, E] =
    Http.fromEffectFunction { req => http(req) map Response.setContentLength }

}
