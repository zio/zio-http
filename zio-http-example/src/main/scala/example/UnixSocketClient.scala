package example

import io.netty.channel.unix.DomainSocketAddress
import zio.http.netty.ChannelType
import zio.http.netty.client.ConnectionPool
import zio.http.{Client, ClientConfig}
import zio.{Console, Scope, ZIOAppDefault}

object UnixSocketClient extends ZIOAppDefault {
  val url        = "http://localhost/v1.41/containers/json"
  val socketPath = "/var/run/docker.sock"
  val uds        = new DomainSocketAddress(socketPath)

  val program = for {
    res  <- Client.request(url, unixSocketAddress = Some(uds))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  override val run = program.provide(
    ConnectionPool.auto,
    ClientConfig.live(ClientConfig.empty.channelType(ChannelType.KQUEUE_UDS)),
    Client.fromConfig,
    Scope.default,
  )

}
