package example.ssl.tls.selfsigned

import zio._
import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {

  val app: ZIO[Client, Throwable, Unit] =
    for {
      _        <- Console.printLine("Making secure HTTPS request to self-signed server...")
      response <- Client.batched(Request.get("https://localhost:8443/hello"))
      body     <- response.body.asString
      _        <- Console.printLine(s"Response status: ${response.status}")
      _        <- Console.printLine(s"Response body: $body")
    } yield ()

  override val run =
    app.provide(
      ZLayer.succeed {
        ZClient.Config.default.ssl(
          ClientSSLConfig.FromTrustStoreResource(
            trustStorePath = "certs/tls/self-signed/truststore.p12",
            trustStorePassword = "trustpass",
          ),
        )
      },
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )
}
