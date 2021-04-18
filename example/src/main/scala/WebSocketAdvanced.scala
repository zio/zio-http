import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketAdvanced extends App {

  // Called after the request is successfully upgraded to websocket
  private val open = Socket.open(_ => ZStream.succeed(WebSocketFrame.text("Greetings!")))

  // Called after the connection is closed
  private val close = Socket.close(_ => console.putStrLn("CLOSED"))

  // Called whenever there is an error on the socket channel
  private val error = Socket.error(_ => console.putStrLn("Error!"))

  // Echos each message that is received on the channel
  private val wsEcho = Socket.collect { case WebSocketFrame.Text(text) =>
    ZStream.repeat(WebSocketFrame.text(s"server:${text}")).schedule(Schedule.spaced(1 second)).take(3)
  }

  // Responds with a close message for each incoming close message
  private val wsClose = Socket.collect { case WebSocketFrame.Close(_, _) =>
    ZStream.succeed(WebSocketFrame.close(1000))
  }

  private val app =
    Http.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings {$name}!")
      case Method.GET -> Root / "subscriptions" => Response.socket(open ++ close ++ error ++ wsEcho ++ wsClose)
      case Method.GET -> Root / "chunked"       =>
        Response.http(
          status = Status.OK,
          content = HttpContent.Chunked(
            ZStream
              .repeat(Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET)))
              .schedule(Schedule.spaced(1 second))
              .take(10),
          ),
        )
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
