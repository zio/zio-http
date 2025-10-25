package example.ssl.tls.rootcasigned

import zio.Config.Secret
import zio._

import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler(Response.text("Hello from TLS server! Connection secured!")),
  )

  private val sslConfig =
    SSLConfig.fromJavaxNetSslKeyStoreResource(
      keyManagerResource = "certs/tls/root-ca-signed/server-keystore.p12",
      keyManagerPassword = Some(Secret("serverkeypass")),
    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(
          sslConfig,
        )
    }

  override val run =
    Server.serve(routes).provide(serverConfig, Server.live)

}
