package example

import zhttp.http.Response
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zhttp.socket.{Socket, WebSocketFrame}
import zio._
import zio.stream.ZStream

object WebSocketSimpleClient extends ZIOAppDefault {

  // Setup client envs
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ Scope.default

  val url = "ws://localhost:8090/subscriptions"

  val app: ZIO[EventLoopGroup with ChannelFactory with Scope, Throwable, Response] = Socket
    .collect[WebSocketFrame] {
      case WebSocketFrame.Text("BAZ") => ZStream.succeed(WebSocketFrame.close(1000))
      case frame                      => ZStream.succeed(frame)
    }
    .toSocketApp
    .connect(url)

  val run = app.exitCode.provideLayer(env)

}
