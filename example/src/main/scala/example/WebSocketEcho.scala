package example

import zhttp.http.{HttpApp, Method, Response, _}
import zhttp.service.Server
import zhttp.socket.{Socket, WebSocketFrame}
import zio.duration._
import zio.stream.ZStream
import zio.{App, ExitCode, Schedule, URIO}

object WebSocketEcho extends App {
  private val socket =
    Socket.collect[WebSocketFrame] {
      case WebSocketFrame.Text("FOO")  => ZStream.succeed(WebSocketFrame.text("BAR"))
      case WebSocketFrame.Text("BAR")  => ZStream.succeed(WebSocketFrame.text("FOO"))
      case WebSocketFrame.Ping         => ZStream.succeed(WebSocketFrame.pong)
      case WebSocketFrame.Pong         => ZStream.succeed(WebSocketFrame.ping)
      case fr @ WebSocketFrame.Text(_) => ZStream.repeat(fr).schedule(Schedule.spaced(1 second)).take(10)
    }

  private val app =
    HttpApp.collect {
      case Method.GET -> !! / "greet" / name  => Response.text(s"Greetings {$name}!")
      case Method.GET -> !! / "subscriptions" => Response.socket(socket)
    }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
