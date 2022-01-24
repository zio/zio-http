package example

import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.{Socket, SocketProtocol, WebSocketFrame}
import zio._
import zio.stream.ZStream

object WebSocketSimpleClient extends zio.App {
  val env     = EventLoopGroup.auto() ++ ChannelFactory.auto
  val url     = "ws://localhost:8090/subscriptions"
  val headers = Headers.empty

  val sa = Socket
    .collect[WebSocketFrame] {
      case WebSocketFrame.Text("BAZ") => ZStream.succeed(WebSocketFrame.close(1000))
      case frame                      => ZStream.succeed(frame)
    }
    .toSocketApp
    .onOpen(Socket.succeed(WebSocketFrame.text("FOO")))
    .onClose(_ => ZIO.unit)
    .onError(thr => ZIO.die(thr))
    .withProtocol(SocketProtocol.uri(url))

  override def run(args: List[String]): URIO[ZEnv, ExitCode] =
    Client
      .socket(headers, sa)
      .flatMap(response => console.putStr(s"${response.status}"))
      .exitCode
      .provideCustomLayer(env)
}
