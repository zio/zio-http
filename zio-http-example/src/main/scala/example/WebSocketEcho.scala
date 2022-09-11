package example

import zio._
import zio.http._
import zio.http.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio.service.ChannelEvent.ChannelRead
import zio.service.{ChannelEvent, Server}

object WebSocketEcho extends ZIOAppDefault {
  private val socket: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
    Http.collectZIO[WebSocketChannelEvent] {
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("FOO"))) =>
        ch.writeAndFlush(WebSocketFrame.text("BAR"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("BAR"))) =>
        ch.writeAndFlush(WebSocketFrame.text("FOO"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(text))) =>
        ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush
    }

  private val app: Http[Any, Nothing, Request, Response] =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => ZIO.succeed(Response.text(s"Greetings {$name}!"))
      case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
    }

  override val run = Server.start(8090, app)
}
