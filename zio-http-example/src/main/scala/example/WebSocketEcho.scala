package example

import zio._
import zio.http.ChannelEvent.ChannelRead
import zio.http._
import zio.http.model.Method
import zio.http.socket.{WebSocketChannelEvent, WebSocketFrame}

object WebSocketEcho extends ZIOAppDefault {
  private val socket: Route[Any, Throwable, WebSocketChannelEvent, Unit] =
    Route.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("FOO"))) =>
        ch.writeAndFlush(WebSocketFrame.text("BAR"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("BAR"))) =>
        ch.writeAndFlush(WebSocketFrame.text("FOO"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(text))) =>
        ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush
    }

  private val app: Route[Any, Nothing, Request, Response] =
    Route.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings {$name}!"))
      case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
    }

  override val run = Server.serve(app).provide(Server.default)
}
