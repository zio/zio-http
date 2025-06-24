package example.ssl.mtls

import zio.Config.Secret
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

  private val config =
    ZClient.Config.default.ssl(
      ClientSSLConfig.FromJavaxNetSsl(
        keyManagerSource = ClientSSLConfig.FromJavaxNetSsl.Resource("certs/mtls/client-keystore.p12"),
        keyManagerPassword = Some(Secret("clientkeypass")),
        trustManagerSource = ClientSSLConfig.FromJavaxNetSsl.Resource("certs/mtls/client-truststore.p12"),
        trustManagerPassword = Some(Secret("clienttrustpass")),
      ),
    )

  override val run =
    app.provide(
      ZLayer.succeed(config),
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
      ZClient.live,
    )

}
