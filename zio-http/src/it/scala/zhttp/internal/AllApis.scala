package zhttp.internal

import zhttp.http._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.stream.ZStream

import java.nio.file.Paths

trait AllApis {
  def file = HttpData.fromStream(ZStream.fromFile(Paths.get("README.md")))

  def socket = Socket.collect[WebSocketFrame] { case WebSocketFrame.Ping =>
    ZStream.succeed(WebSocketFrame.pong)
  }

  def app = HttpApp.collect {
    case Method.GET -> !!                     => Response.ok
    case Method.POST -> !!                    => Response.status(Status.CREATED)
    case Method.PUT -> !!                     => Response.status(Status.NO_CONTENT)
    case Method.DELETE -> !!                  => Response.status(Status.NO_CONTENT)
    case Method.GET -> !! / "boom"            => Response.status(Status.INTERNAL_SERVER_ERROR)
    case Method.GET -> !! / "subscriptions"   => Response.socket(socket)
    case Method.GET -> !! / "stream" / "file" => Response.http(content = file)
  }
}
