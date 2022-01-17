package example
import zhttp.http.Headers
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.{Socket, SocketApp, SocketProtocol, WebSocketFrame}
import zio.stream.ZStream
import zio.{ExitCode, URIO, ZIO}

object WebSocketClient extends zio.App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  private val open   = Socket.succeed(WebSocketFrame.Text("FOO"))
  private val socket = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("BAR")  => ZStream.succeed(WebSocketFrame.text("BAZ"))
    case WebSocketFrame.Text("KILL") => ZStream.succeed(WebSocketFrame.close(1000))
  }

  val protocol   = SocketProtocol.uri("ws://localhost:8090/subscriptions")
  private val ss = SocketApp(socket)
    .onOpen(open)
    .onClose(_ => ZIO.unit)
    .onError(thr => ZIO.die(thr))
    .onTimeout(zio.console.putStrLn("Timed out").orDie)
    .withProtocol(protocol)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Client
      .socket(Headers.empty, ss)
      .exitCode
      .provideCustomLayer(env)
}
