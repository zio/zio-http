package zhttp.service.client.experimental.model

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.client.experimental.Resp
import zhttp.service.client.experimental.model.ZConnectionState.{ReqKey, emptyConnectionRuntime, emptyIdleConnectionMap}
import zio.Promise
import zio.duration.Duration

import java.net.InetSocketAddress
import java.time.Instant
import scala.collection.mutable

//
case class ConnectionRuntime(callback: Promise[Throwable, Resp], currReq: FullHttpRequest)

case class Timeouts(
  connectionTimeout: Duration = Duration.Infinity,
  idleTimeout: Duration = Duration.Infinity,
  requestTimeout: Duration = Duration.Infinity,
)

case class PendingRequest(req: FullHttpRequest, requestedTime: Instant)

// TBD: Choose which data structures or a group of data structures to be made thread safe
case class ZConnectionState(
  currentAllocatedChannels: mutable.Map[Channel, ConnectionRuntime] = emptyConnectionRuntime,
  currentAllocatedRequests: mutable.Map[ReqKey, Int] = mutable.Map.empty[ReqKey, Int],
  idleConnectionsMap: mutable.Map[ReqKey, mutable.Queue[ConnectionRuntime]] = emptyIdleConnectionMap,
  waitingRequestQueue: mutable.Queue[PendingRequest] = mutable.Queue.empty[PendingRequest],
) {
  // TBD thready safety and appropriate namespace
  var currMaxTotalConnections: Int     = 0
  var currMaxConnectionPerRequest: Int = 0
  var currMaxWaitingReq: Int           = 0

}

object ZConnectionState {
  type ReqKey = InetSocketAddress
  def emptyConnectionRuntime = mutable.Map.empty[Channel, ConnectionRuntime]
  def emptyIdleConnectionMap = mutable.Map.empty[ReqKey, mutable.Queue[ConnectionRuntime]]
}
