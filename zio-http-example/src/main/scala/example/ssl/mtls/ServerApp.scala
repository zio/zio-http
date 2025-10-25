package example.ssl.mtls

import zio.Config.Secret
import zio._

import zio.http.SSLConfig.Data.FromJavaxNetSsl
import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler { (req: Request) =>
        ZIO.debug(req.remoteCertificate) *> ZIO.succeed(
          Response.text("Hello from TLS server! Connection secured!"),
        )
      },
  )

  private val sslConfig =
    SSLConfig.fromJavaxNetSsl(
      data = SSLConfig.Data.FromJavaxNetSsl(
        keyManagerSource = FromJavaxNetSsl.Resource("certs/mtls/server-keystore.p12"),
        keyManagerPassword = Some(Secret("serverkeypass")),
        trustManagerKeyStore = Some(
          SSLConfig.Data.TrustManagerKeyStore(
            trustManagerSource = FromJavaxNetSsl.Resource("certs/mtls/server-truststore.p12"),
            trustManagerPassword = Some(Secret("servertrustpass")),
          ),
        ),
      ),
      includeClientCert = false,
      clientAuth = Some(ClientAuth.Required),
    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(sslConfig)
    }

  override val run =
    Server.serve(routes).provide(serverConfig, Server.live)

}
