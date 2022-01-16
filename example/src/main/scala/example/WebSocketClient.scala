package example
import zhttp.http.{Headers, URL}
import zhttp.service.{ChannelFactory, Client, EventLoopGroup}
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.console.putStrLn
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
    .onClose(con => putStrLn(s"Closing connection: ${con}").orDie)
    .onError(con => putStrLn(s"Error: ${con}").orDie)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = ZIO
    .fromEither(URL.fromString(url))
    .flatMap(url => Client.socket(url = url, Headers.empty, app = ss))
    .flatMap(queue => ZStream.fromQueue(queue).runCollect)
    .flatMap(chunk => putStrLn(chunk.toList.toString))
    .exitCode
    .provideCustomLayer(env)
}
