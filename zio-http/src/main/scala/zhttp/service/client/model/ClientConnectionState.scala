package zhttp.service.client.model

import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.client.model.ConnectionData.ReqKey
import zio.Ref
import zio.duration.Duration

import java.net.InetSocketAddress
import java.time.Instant
import scala.collection.immutable

case class Connection(channel: Channel, isReuse: Boolean) {
  override def canEqual(that: Any): Boolean = {
    this.channel.id == that.asInstanceOf[Connection].channel.id()
  }
}

case class Timeouts(
  connectionTimeout: Duration = Duration.Infinity,
  idleTimeout: Duration = Duration.Infinity,
  requestTimeout: Duration = Duration.Infinity,
)

case class PendingRequest(req: FullHttpRequest, requestedTime: Instant)

case class ConnectionState(
  currentAllocatedChannels: Map[Channel, ReqKey],
  idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
)

case class ConnectionData(connectionData: Ref[(Option[Connection], ConnectionState)]) {

  def gic(reqKey: ReqKey) = for {
    getClientStateData <- connectionData.updateAndGet { clientData =>
      getIdleChannel(reqKey, clientData._2.currentAllocatedChannels, clientData._2.idleConnectionsMap)
    }
  } yield (getClientStateData._1)

  def getIdleChannel(
    reqKey: ReqKey,
    currentAllocatedChannels: Map[Channel, ReqKey],
    idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
  ) = {
    val idleMap      =
      if (idleConnectionsMap.get(reqKey).isEmpty) (idleConnectionsMap + (reqKey -> immutable.Queue.empty[Connection]))
      else idleConnectionsMap
    val idleQ        = idleMap(reqKey)
    // if no queue exists for this req key add an empty queue
    val defaultState = (None, ConnectionState(currentAllocatedChannels, idleMap))
    val res          =
      if (idleQ.isEmpty) defaultState
      else {
        val conn = idleQ.dequeue
        if (conn == null) defaultState
        else {
          val newCurrMap = currentAllocatedChannels.updated(conn._1.channel, reqKey)
          //        println(s"newCurrMap: $newCurrMap")
          val newIdleMap = idleMap.updated(reqKey, conn._2)
          (Some(conn._1), ConnectionState(newCurrMap, newIdleMap))
        }
      }
    res
  }

  def aic(connection: Connection, reqKey: ReqKey) = {
    connectionData.updateAndGet { clientData =>
      addIdleChannel(connection, reqKey, clientData._2.currentAllocatedChannels, clientData._2.idleConnectionsMap)
    }
  }

  def addIdleChannel(
    connection: Connection,
    reqKey: ReqKey,
    currentAllocatedChannels: Map[Channel, ReqKey],
    idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
  ) = {
    val currMap = if (currentAllocatedChannels.contains(connection.channel)) {
      currentAllocatedChannels - (connection.channel)
    } else currentAllocatedChannels

    val idleMap =
      if (idleConnectionsMap.get(reqKey).isEmpty) Map(reqKey -> immutable.Queue(connection))
      else {
        val q         = idleConnectionsMap(reqKey)
        // FIXME: Is it needed? if yes replace it with something performant.
        val isPresent = !q.filter(_.channel.id() == connection.channel.id()).isEmpty
        if (isPresent) idleConnectionsMap
        else Map(reqKey -> q.enqueue(connection))
      }

    (None, ConnectionState(currMap, idleMap))
  }

  def getTotalConnections = for {
    connectionData <- connectionData.get
    allocConnections = connectionData._2.currentAllocatedChannels.size
    idleConnections  = connectionData._2.idleConnectionsMap.valuesIterator
      .foldLeft(0) { case (acc, queue) => acc + queue.size }
  } yield (allocConnections + idleConnections)

  //    def incrementConnection: Unit = ???
  //    def decrementConnection = ???
  //    def isConnectionExpired = ???
  //    def isConnectionWithinLimits = ???
  //    def addConnectionToIdleQ = ???
  //    def addConnectionToWaitQ = ???
  //
  //    def releaseConnection = ???
  //    def shutdownConnectionManager = ???
  //
  //    def getActiveConnectionsForReqKey(reqKey: ReqKey): Task[Int] = ???
  //    def getIdleConnections: Task[Int] = ???
  //    def getIdleConnectionsForReqKey(reqKey: ReqKey): Task[Int] = ???

  // TBD thready safety and appropriate namespace
  var currMaxTotalConnections: Int     = 0
  var currMaxConnectionPerRequest: Int = 0
  var currMaxWaitingReq: Int           = 0

}

object ConnectionData {
  type ReqKey = InetSocketAddress
  def emptyIdleConnectionMap = Map.empty[ReqKey, immutable.Queue[Connection]]
}
