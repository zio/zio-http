package zhttp.service.client.domain

import io.netty.channel.Channel
import zhttp.service.client.domain.ConnectionData.ReqKey
import zio.Ref
import zio.duration.Duration

import java.net.InetSocketAddress
import scala.collection.immutable

/**
 * Defines ClientData / Request Key and other types for managing connection data
 * TODO: Likely to change
 *
 * @param channel
 * @param isReuse
 */
case class Connection(channel: Channel, isReuse: Boolean) {
  override def canEqual(that: Any): Boolean = that match {
    case that: Connection => this.channel.id() == that.channel.id()
    case _                => false
  }
}

case class Timeouts(
  connectionTimeout: Duration = Duration.Infinity,
  idleTimeout: Duration = Duration.Infinity,
  requestTimeout: Duration = Duration.Infinity,
)

case class ConnectionPoolState(
  currentAllocatedChannels: Map[Channel, ReqKey],
  idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]],
)

case class NewConnectionData(newConnection: Option[Connection] = None, connectionPoolState: ConnectionPoolState)

case class ConnectionData(connectionData: Ref[NewConnectionData]) {

  def nextIdleChannel(reqKey: ReqKey) = for {
    getClientStateData <- connectionData.updateAndGet { clientData =>
      getIdleChannel(
        reqKey,
        clientData.connectionPoolState.currentAllocatedChannels,
        clientData.connectionPoolState.idleConnectionsMap,
      )
    }
  } yield (getClientStateData.newConnection)

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
    val defaultState = NewConnectionData(None, ConnectionPoolState(currentAllocatedChannels, idleMap))
    val res          =
      if (idleQ.isEmpty) defaultState
      else {
        val conn = idleQ.dequeue
        if (conn == null) defaultState
        else {
          val newCurrMap = currentAllocatedChannels.updated(conn._1.channel, reqKey)
          //        println(s"newCurrMap: $newCurrMap")
          val newIdleMap = idleMap.updated(reqKey, conn._2)
          NewConnectionData(Some(conn._1), ConnectionPoolState(newCurrMap, newIdleMap))
        }
      }
    res
  }

  def setConnectionIdle(connection: Connection, reqKey: ReqKey) = {
    connectionData.updateAndGet { clientData =>
      addIdleChannel(
        connection,
        reqKey,
        clientData.connectionPoolState.currentAllocatedChannels,
        clientData.connectionPoolState.idleConnectionsMap,
      )
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

    NewConnectionData(None, ConnectionPoolState(currMap, idleMap))
  }
  def getTotalConnections                                       = for {
    connectionData <- connectionData.get
    allocConnections = connectionData.connectionPoolState.currentAllocatedChannels.size
    idleConnections  = connectionData.connectionPoolState.idleConnectionsMap.valuesIterator
      .foldLeft(0) { case (acc, queue) => acc + queue.size }
  } yield (allocConnections + idleConnections)

  // TBD thready safety and appropriate namespace
  var currMaxTotalConnections: Int     = 0
  var currMaxConnectionPerRequest: Int = 0
  var currMaxWaitingReq: Int           = 0

}

object ConnectionData {
  type ReqKey = InetSocketAddress
  def emptyIdleConnectionMap = Map.empty[ReqKey, immutable.Queue[Connection]]
}
