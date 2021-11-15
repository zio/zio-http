import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.stream.ZStream

object SimpleSocketProvideServer extends App {
  private val socket: Socket[Any, Nothing, WebSocketFrame, WebSocketFrame] = Socket
    .collect[WebSocketFrame] { case WebSocketFrame.Text("FOO") =>
      ZStream.environment[WebSocketFrame].map(identity)
    }
    .provide(WebSocketFrame.text("BAR"))

  private val app =
    HttpApp.collect { case Method.GET -> !! / "subscriptions" => Response.socket(socket) }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(80, app).exitCode
}
