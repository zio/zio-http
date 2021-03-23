import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object SocketEchoServer extends App {
  private val socket =
    Socket.forall[WebSocketFrame](msg => ZStream.repeat(msg).schedule(Schedule.spaced(1 second)).take(10))

  implicit val httpNothingPartial: CanSupportPartial[Request, Nothing] = (_: Request) => Nil.head

  private val app =
    HttpChannel.collectM[Request] {
      case Method.GET -> Root / "greet" / name  => UIO(Response.text(s"Greetings {$name}!"))
      case Method.GET -> Root / "subscriptions" => socket.asResponse(None)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
