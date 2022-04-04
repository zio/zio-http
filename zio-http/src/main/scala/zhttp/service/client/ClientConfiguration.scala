package zhttp.service.client
import io.netty.channel.pool.ChannelPool
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.socket.SocketApp

final case class ClientConfiguration(
  socketApp: Option[SocketApp[Any]] = None,
  ssl: Option[ClientSSLOptions] = None,
  connectionPool: Option[ChannelPool] = None,
)
