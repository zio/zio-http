package example.ssl.tls.intermediatecasigned

import zio._

import zio.http._
import zio.http.netty.NettyConfig

object ClientApp extends ZIOAppDefault {

  val app: ZIO[Client, Throwable, Unit] = for {
    _             <- Console.printLine("\nMaking HTTPS request to /hello")
    helloResponse <- ZClient.batched(Request.get("https://localhost:8443/hello"))
    helloBody     <- helloResponse.body.asString
    _             <- Console.printLine(s"Response Status: ${helloResponse.status}")
    _             <- Console.printLine(s"Response: $helloBody")
  } yield ()

  override val run = app.provide(
    ZLayer.succeed {
      ZClient.Config.default.ssl(
        ClientSSLConfig.FromTrustStoreResource(
          trustStorePath = "certs/tls/intermediate-ca-signed/client-truststore.p12",
          trustStorePassword = "clienttrustpass",
        ),
      )
    },
    ZLayer.succeed(NettyConfig.default),
    DnsResolver.default,
    ZClient.live,
  )
}
