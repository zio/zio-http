package example

import zhttp.service.{ChannelFactory, EventLoopGroup}
import zhttp.socket.{Socket, WebSocketFrame}
import zio._
import zio.stream.ZStream

object WebSocketSimpleClient extends ZIOAppDefault {

  // Setup client envs
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

  val url = "ws://ws.vi-server.org/mirror"

  val app = Socket
    .collect[WebSocketFrame] {
      case WebSocketFrame.Text("BAZ") => ZStream.succeed(WebSocketFrame.close(1000))
      case frame                      => ZStream.succeed(frame)
    }
    .toSocketApp
    .connect(url)

  val run = ZIO.scoped { app *> ZIO.never }.exitCode.provideLayer(env)

}
