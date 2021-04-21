import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketAdvanced extends App {
  // Message Handlers
  private val open   = Message.succeed(WebSocketFrame.text("Greetings!"))
  private val echo   = Message.collect[WebSocketFrame] { case WebSocketFrame.Text(text) =>
    ZStream.repeat(WebSocketFrame.text(s"Received: $text")).schedule(Schedule.spaced(1 second)).take(3)
  }
  private val fooBar = Message.collect[WebSocketFrame] {
    case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
    case WebSocketFrame.Text("BAR") => ZStream.succeed(WebSocketFrame.text("FOO"))
  }

  // Combine all channel handlers together
  private val channel =
    SocketChannel.open(open) ++                                // Called after the request is successfully upgraded to websocket
      SocketChannel.message(echo merge fooBar) ++              // Called after each message being received on the channel
      SocketChannel.close(_ => console.putStrLn("Closed!")) ++ // Called after the connection is closed
      SocketChannel.error(_ => console.putStrLn("Error!"))     // Called whenever there is an error on the socket channel

  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  private val app =
    Http.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings ${name}!")
      case Method.GET -> Root / "subscriptions" => Response.socket(channel +++ protocol)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
