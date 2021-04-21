import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketAdvanced extends App {

  // Called after the request is successfully upgraded to websocket
  private val open = SocketChannel.open(Message.succeed(WebSocketFrame.text("Greetings!")))

  // Called after the connection is closed
  private val close = SocketChannel.close(_ => console.putStrLn("CLOSED"))

  // Called whenever there is an error on the socket channel
  private val error = SocketChannel.error(_ => console.putStrLn("Error!"))

  // Echos each message that is received on the channel
  private val wsEcho = SocketChannel.message {
    Message.collect { case WebSocketFrame.Text(text) =>
      ZStream.repeat(WebSocketFrame.text(s"server:${text}")).schedule(Schedule.spaced(1 second)).take(3)
    }
  }

  // Responds with a close message for each incoming close message
  private val wsClose = SocketChannel.message {
    Message.collect { case WebSocketFrame.Close(_, _) =>
      ZStream.succeed(WebSocketFrame.close(1000))
    }
  }

  // Combine all channel handlers together
  private val channel = open ++ close ++ error ++ wsEcho ++ wsClose

  // Setup protcol settings
  private val protocol = SocketProtocol.subProtocol("json")

  private val app =
    Http.collect {
      case Method.GET -> Root / "greet" / name  => Response.text(s"Greetings ${name}!")
      case Method.GET -> Root / "subscriptions" => Response.socket(channel +++ protocol)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
