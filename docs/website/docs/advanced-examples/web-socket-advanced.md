# Web Socket Server
```scala
import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketAdvanced extends App {
  // Message Handlers
  private val open = Socket.succeed(WebSocketFrame.text("Greetings!"))

  private val echo = Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(text) =>
    ZStream.repeat(WebSocketFrame.text(s"Received: $text")).schedule(Schedule.spaced(1 second)).take(3)
  }

  private val fooBar = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
    case WebSocketFrame.Text("BAR") => ZStream.succeed(WebSocketFrame.text("FOO"))
  }

  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  // Setup decoder settings
  private val decoder = SocketDecoder.allowExtensions

  // Combine all channel handlers together
  private val socketApp =
    SocketApp.open(open) ++ // Called after the request is successfully upgraded to websocket
      SocketApp.message(echo merge fooBar) ++ // Called after each message being received on the channel
      SocketApp.close(_ => console.putStrLn("Closed!").ignore) ++ // Called after the connection is closed
      SocketApp.error(_ =>
        console.putStrLn("Error!").ignore,
      ) ++ // Called whenever there is an error on the socket channel
      SocketApp.decoder(decoder) ++
      SocketApp.protocol(protocol)

  private val app =
    HttpApp.collect {
      case Method.GET -> !! / "greet" / name  => Response.text(s"Greetings ${name}!")
      case Method.GET -> !! / "subscriptions" => socketApp
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}

```