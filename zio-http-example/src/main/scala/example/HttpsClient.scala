package example

import zio._

import zio.http.model.{Header, Headers}
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientBackend
import zio.http.{Client, ClientSSLConfig, DnsResolver, ZClient}

object HttpsClient extends ZIOAppDefault {
  val url     = "https://sports.api.decathlon.com/groups/water-aerobics"
  val headers = Headers(Header.Host("sports.api.decathlon.com"))

  val sslConfig = ClientSSLConfig.FromTrustStoreResource(
    trustStorePath = "truststore.jks",
    trustStorePassword = "changeit",
  )

  val clientConfig = ZClient.Config.default.ssl(sslConfig)

  val program = for {
    res  <- Client.request(url, headers = headers)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val run =
    program.provide(
      ZLayer.succeed(clientConfig),
      Client.customized,
      NettyClientBackend.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.default),
    )

}
