import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.json._
import zio.stream.ZStream

object WebSocketAdvanced extends App {

  sealed trait Command extends Product with Serializable

  object Command {
    case object Foo extends Command
    case object Bar extends Command
    case object Baz extends Command

    implicit val jEncoder = DeriveJsonEncoder.gen[Command]
    implicit val jDecoder = DeriveJsonDecoder.gen[Command]
  }

  private val wsCommand = Socket.collect[Command] {
    case Command.Foo => ZStream.succeed(Command.Bar)
    case Command.Bar => ZStream.succeed(Command.Foo)
  }

  // Echos each message that is received on the channel
  private val wsEcho = Socket.collect[String] { case text =>
    ZStream.repeat(s"server:${text}").schedule(Schedule.spaced(1 second)).take(3)
  }

  private val wsPing = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Ping => ZStream.succeed(WebSocketFrame.pong)
    case WebSocketFrame.Pong => ZStream.succeed(WebSocketFrame.ping)
  }

  // Called after the request is successfully upgraded to websocket
  private val open = Socket.open(_ => ZStream.succeed(WebSocketFrame.text("Greetings!")))

  // Called after the connection is closed
  private val close = Socket.close(_ => console.putStrLn("CLOSED"))

  // Called whenever there is an error on the socket channel
  private val error = Socket.error(_ => console.putStrLn("Error!"))

  // Responds with a close message for each incoming close message
  private val wsClose = Socket.collect[WebSocketFrame] { case WebSocketFrame.Close(_, _) =>
    ZStream.succeed(WebSocketFrame.close(1000))
  }

  private val app =
    Http.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
      case Method.GET -> Root / "subscriptions" =>
        Response.socket(open ++ close ++ error ++ wsEcho ++ wsPing ++ wsClose ++ wsCommand)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
