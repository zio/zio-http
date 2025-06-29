package example.ssl.tls.selfsigned

import zio.Config.Secret
import zio._

import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" ->
      handler(Response.text("Hello from self-signed TLS server! Connection secured!")),
  )

  // Option 1: Using PKCS12 keystore (recommended)
  private val sslConfig =
    SSLConfig.fromJavaxNetSslKeyStoreResource(
      keyManagerResource = "certs/tls/self-signed/server.p12",
      keyManagerPassword = Some(Secret("changeit")),
    )

//  Option 2: Using PEM files directly
//  Note: This might require the PEM files to be in the correct format
//  private val sslConfigPem =
//    SSLConfig.fromResource(
//      certPath = "certs/tls/self-signed/server-cert.pem",
//      keyPath = "certs/tls/self-signed/server-key.pem",
//    )

  private val serverConfig =
    ZLayer.succeed {
      Server.Config.default
        .port(8443)
        .ssl(sslConfig) // Using PKCS12 keystore
    }

  val run =
    Console.printLine("Self-Signed TLS Server starting on https://localhost:8443/") *>
      Server.serve(routes).provide(serverConfig, Server.live)
}
