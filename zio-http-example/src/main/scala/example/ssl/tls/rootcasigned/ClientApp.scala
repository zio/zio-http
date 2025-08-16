package example.ssl.tls.rootcasigned

import zio._

import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {

  val app: ZIO[Client, Throwable, Unit] =
    for {
      _            <- Console.printLine("Making secure HTTPS requests...")
      textResponse <- Client.batched(
        Request.get("https://localhost:8443/hello"),
      )
      textBody     <- textResponse.body.asString
      _            <- Console.printLine(s"Text response: $textBody")
    } yield ()

//  private val sslConfig =
//    SSLConfig.fromResource(
//      certPath = "certs/tls/root-ca-signed/server-cert.pem",
//      keyPath = "certs/tls/root-ca-signed/server-key.pem",
//    )

  private val sslConfig =
    ZClient.Config.default.ssl(
      ClientSSLConfig.FromTrustStoreResource(
        "certs/tls/root-ca-signed/client-truststore.p12",
        "clienttrustpass",
      ),
    )

  override val run =
    app.provide(
      ZLayer.succeed(sslConfig),
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )

}
