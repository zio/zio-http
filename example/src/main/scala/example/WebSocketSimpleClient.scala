package example

import zhttp.service.{ChannelFactory, EventLoopGroup}
import zhttp.socket.{Socket, WebSocketFrame}
import zio._
import zio.stream.ZStream

object WebSocketSimpleClient extends zio.App {

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

  override def run(args: List[String]): URIO[ZEnv, ExitCode] = {
    app.useForever.exitCode.provideCustomLayer(env)
  }
}
