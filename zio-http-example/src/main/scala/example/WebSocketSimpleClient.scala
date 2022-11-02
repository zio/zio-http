package example

import zio._
import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.socket.{SocketApp, SocketAppAction, SocketAppEvent, WebSocketChannelEvent, WebSocketFrame}
import zio.http.{ChannelEvent, Client, Http, Response}

object WebSocketSimpleClient extends ZIOAppDefault {

  val url = "ws://ws.vi-server.org/mirror"

  val socketApp = SocketApp {
    case SocketAppEvent.Connected(_) => ZIO.succeed(SocketAppAction.SendFrame(WebSocketFrame.text("foo")).withFlush)
    case SocketAppEvent.FrameReceived(WebSocketFrame.Text("foo")) => ZIO.succeed(SocketAppAction.SendFrame(WebSocketFrame.text("bar")).withFlush)
    case SocketAppEvent.FrameReceived(WebSocketFrame.Text("bar")) =>
      println("Goodbye!")
      ZIO.succeed(SocketAppAction.SendFrame(WebSocketFrame.close(1000)).withFlush)
  }

  val app: ZIO[Any with Client with Scope, Throwable, Response] = {
    Client.socket(url, socketApp)
  }

  val run = app.provide(Client.default, Scope.default)

}
