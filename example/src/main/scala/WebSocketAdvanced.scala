import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketAdvanced extends App {

  // Called after the request is successfully upgraded to websocket
  private val open = SocketBuilder.open(_ => console.putStrLn("OPENED"))

  // Called after the connection is closed
  private val close = SocketBuilder.close((_, _) => console.putStrLn("CLOSED"))

  // Called whenever there is an error on the socket channel
  private val error = SocketBuilder.error(_ => console.putStrLn("Error!"))

  // Called for each message received on the channel
  private val message = SocketBuilder.collect({
    case WebSocketFrame.Close(_, _) => ZStream.succeed(WebSocketFrame.close(1000))
    case WebSocketFrame.Text(text)  =>
      ZStream.repeat(WebSocketFrame.text(s"server:${text}")).schedule(Schedule.spaced(1 second)).take(3)
  })

  private val app =
    Http.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
      case Method.GET -> Root / "subscriptions" => Response.socket(open ++ close ++ error ++ message)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
