package zhttp.service.client.experimental

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.client.experimental.ZConnectionState.{emptyConnectionRuntime}
import zio.Promise

import scala.collection.mutable

case class ConnectionRuntime(conn: Promise[Throwable,Resp], currReq: FullHttpRequest)
case class RequestWaiting(req: FullHttpRequest)
case class ZConnectionState(
  currentAllocated: mutable.Map[Channel, ConnectionRuntime] = emptyConnectionRuntime,
  idleConnections: mutable.Queue[ConnectionRuntime]
)

object ZConnectionState{
  def emptyConnectionRuntime = mutable.Map.empty[Channel, ConnectionRuntime]
}
