package example

import zio.http.netty.ChannelType
import zio.http.netty.client.ConnectionPool
import zio.http.{Client, ClientConfig}
import zio.{Console, Scope, ZIOAppDefault}

object UnixSocketClient extends ZIOAppDefault {
  val url = "http+unix://%2Fvar%2Frun%2Fdocker.sock/info"

  val program = for {
    res  <- Client.request(url)
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
