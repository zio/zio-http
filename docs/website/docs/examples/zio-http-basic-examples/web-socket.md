# Simple Websocket Server

```scala
import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.duration._
import zio.stream.ZStream

object WebSocketEcho extends ZIOAppDefault {
  private val socket =
    Socket.collect[WebSocketFrame] {
      case WebSocketFrame.Text("FOO")  => ZStream.succeed(WebSocketFrame.text("BAR"))
      case WebSocketFrame.Text("BAR")  => ZStream.succeed(WebSocketFrame.text("FOO"))
      case WebSocketFrame.Ping         => ZStream.succeed(WebSocketFrame.pong)
      case WebSocketFrame.Pong         => ZStream.succeed(WebSocketFrame.ping)
      case fr @ WebSocketFrame.Text(_) => ZStream.repeat(fr).schedule(Schedule.spaced(1 second)).take(10)
    }

  private val app =
    Http.collectZIO[Request] {
      case Method.GET -> !! / "greet" / name  => Response.text(s"Greetings {$name}!").wrapZIO
      case Method.GET -> !! / "subscriptions" => socket.toResponse
    }

  override val run =
    Server.start(8090, app)
}

```
