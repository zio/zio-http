package zhttp.service.client.model

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.ClientResponse
import zhttp.service.client.model.ClientConnectionState.{ReqKey, emptyIdleConnectionMap}
import zio.{Promise, Ref}
import zio.duration.Duration

import java.net.InetSocketAddress
import java.time.Instant
import scala.collection.mutable

//
case class ConnectionRuntime(callback: Promise[Throwable, ClientResponse], currReq: FullHttpRequest, reqKey: ReqKey)

case class Timeouts(
  connectionTimeout: Duration = Duration.Infinity,
  idleTimeout: Duration = Duration.Infinity,
  requestTimeout: Duration = Duration.Infinity,
)

case class PendingRequest(req: FullHttpRequest, requestedTime: Instant)

// TBD: Choose which data structures or a group of data structures to be made thread safe
case class ClientConnectionState(
  currentAllocatedChannels: Ref[Map[Channel, ConnectionRuntime]],
  currentAllocatedRequests: Map[ReqKey, Int] = Map.empty[ReqKey, Int],
  idleConnectionsMap: Map[ReqKey, mutable.Queue[Channel]] = emptyIdleConnectionMap,
  waitingRequestQueue: mutable.Queue[PendingRequest] = mutable.Queue.empty[PendingRequest],
) {
  // TBD thready safety and appropriate namespace
  var currMaxTotalConnections: Int     = 0
  var currMaxConnectionPerRequest: Int = 0
  var currMaxWaitingReq: Int           = 0

}

object ClientConnectionState {
  type ReqKey = InetSocketAddress
  def emptyIdleConnectionMap = Map.empty[ReqKey, mutable.Queue[Channel]]
}
