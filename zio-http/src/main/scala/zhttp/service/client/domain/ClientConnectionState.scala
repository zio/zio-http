package zhttp.service.client.domain

import io.netty.channel.Channel

import java.net.InetSocketAddress
import scala.collection.immutable

/**
 * Defines ClientData / Request Key and other types for managing connection data
 * TODO: Likely to change
 *
 * @param channel
 *   the low level connection abstraction from netty
 * @param isReuse
 *   is channel newly created or being re-used.
 */
case class Connection(channel: Channel, isReuse: Boolean) {
  override def canEqual(that: Any): Boolean = that match {
    case that: Connection => this.channel.id() == that.channel.id()
    case _                => false
  }
}

object ConnectionData {
  type ReqKey = InetSocketAddress
  def emptyIdleConnectionMap = Map.empty[ReqKey, immutable.Queue[Connection]]
}
