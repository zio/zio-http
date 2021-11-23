package zhttp

import zhttp.http._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.ZIO
import zio.duration.durationInt
import zio.stream.ZStream

object AllApis {
  def socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Ping =>
    ZStream.succeed(WebSocketFrame.pong)
  }

  def api = HttpApp.collect {
    case Method.GET -> !!                   => Response.ok
    case Method.POST -> !!                  => Response.status(Status.CREATED)
    case Method.GET -> !! / "continue"      => Response.ok
    case Method.GET -> !! / "boom"          => Response.status(Status.INTERNAL_SERVER_ERROR)
    case Method.GET -> !! / "subscriptions" => Response.socket(socket)
  }

  def apiM = HttpApp.collectM { case Method.GET -> !! / "timeout" =>
    ZIO.sleep(100 seconds).as(Response.ok)
  }

  def apply() = api +++ apiM
}
