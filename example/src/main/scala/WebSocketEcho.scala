import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketEcho extends App {
  private val socket =
    Socket.collect[HWebSocketFrame] {
      case HWebSocketFrame.Text("FOO")  => ZStream.succeed(HWebSocketFrame.text("BAR"))
      case HWebSocketFrame.Text("BAR")  => ZStream.succeed(HWebSocketFrame.text("FOO"))
      case HWebSocketFrame.Ping         => ZStream.succeed(HWebSocketFrame.pong)
      case HWebSocketFrame.Pong         => ZStream.succeed(HWebSocketFrame.ping)
      case fr @ HWebSocketFrame.Text(_) => ZStream.repeat(fr).schedule(Schedule.spaced(1 second)).take(10)
    }

  private val app =
    HttpApp.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
      case Method.GET -> Root / "subscriptions" => Response.socket(socket)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
