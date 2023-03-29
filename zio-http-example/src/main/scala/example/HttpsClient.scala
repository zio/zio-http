package example

import zio._

import zio.http.model.{Header, Headers}
import zio.http.netty.client.NettyClientDriver
import zio.http.{Client, ClientConfig, ClientSSLConfig, DnsResolver}

object HttpsClient extends ZIOAppDefault {
  val url     = "https://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers(Header.Host("sports.api.decathlon.com"))

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  val clientConfig = ClientConfig.empty.ssl(sslConfig)

  val program = for {
    res  <- Client.request(url, headers = headers)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val run =
    program.provide(ClientConfig.live(clientConfig), Client.live, NettyClientDriver.fromConfig, DnsResolver.default)

}
