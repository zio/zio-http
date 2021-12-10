package example

import zhttp.http._
import zhttp.service._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.ZStream
import zio.{ExitCode, URIO}

object WebSocketProvide extends zio.App {
  private val socket = Socket
    .collect[WebSocketFrame] { case WebSocketFrame.Text("FOO") => ZStream.environment[WebSocketFrame] }
    .provide(WebSocketFrame.text("BAR"))

  private val app = Http.collect[Request] { case Method.GET -> !! / "ws" => Response.socket(socket) }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    val port = 80
    Server
      .start(port, app)
      .debug(s"Server Started on port ${port}")
      .exitCode
  }
}
