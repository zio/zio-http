package example
import zhttp.http.{Headers, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.stream.ZStream
import zio.{ExitCode, URIO, ZIO}

object WebSocketClient extends zio.App {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto()
  val url = "ws://localhost:8090/subscriptions"

  private val open   = Socket.succeed(WebSocketFrame.Text("FOO"))
  private val socket = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("BAR")  => ZStream.succeed(WebSocketFrame.text("BAZ"))
    case WebSocketFrame.Text("KILL") => ZStream.succeed(WebSocketFrame.close(1000))
  }

  private val ss = SocketApp(socket)
    .onOpen(open)
    .onClose(_ => ZIO.unit)
    .onError(thr => ZIO.die(thr))
    .onTimeout(zio.console.putStrLn("Timed out").orDie)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      _   <- Client.socket(url, Headers.empty, ss)
    } yield ()
  }.exitCode
    .provideCustomLayer(env)
}
