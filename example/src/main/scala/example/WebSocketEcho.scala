package example

import zhttp.http._
import zhttp.service.ChannelEvent.Event.ChannelRead
import zhttp.service.{ChannelEvent, Server}
import zhttp.socket.{WebSocketChannelEvent, WebSocketFrame}
import zio.{App, ExitCode, UIO, URIO}

object WebSocketEcho extends App {
  private val socket =
    Http.collect[WebSocketChannelEvent] {
      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("FOO"))) =>
        ch.writeAndFlush(WebSocketFrame.text("BAR"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text("BAR"))) =>
        ch.writeAndFlush(WebSocketFrame.text("FOO"))

      case ChannelEvent(ch, ChannelRead(WebSocketFrame.Text(text))) =>
        ch.write(WebSocketFrame.text(text)).repeatN(10) *> ch.flush
    }

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => UIO(Response.text(s"Greetings {$name}!"))
      case Method.GET -> !! / "subscriptions" => socket.toSocketApp.toResponse
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
