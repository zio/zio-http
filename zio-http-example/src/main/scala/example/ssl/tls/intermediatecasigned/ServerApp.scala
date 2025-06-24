package example.ssl.tls.intermediatecasigned

import java.io.FileInputStream
import java.security.KeyStore
import java.security.cert.X509Certificate

import scala.util.Try

import zio.Config.Secret
import zio._

import zio.http._

object ServerApp extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / "hello" -> handler {
      Response.text(
        """Hello from TLS server with certificate chain!
          |The server sent a certificate chain for verification.
          |""".stripMargin,
      )
    },
  )

  // SSL configuration using PKCS12 keystore with certificate chain
  private val sslConfig = SSLConfig.fromJavaxNetSslKeyStoreResource(
    keyManagerResource = "certs/tls/intermediate-ca-signed/server.p12",
    keyManagerPassword = Some(Secret("changeit")),
  )

  private val serverConfig = ZLayer.succeed {
    Server.Config.default
      .port(8443)
      .ssl(sslConfig)
  }

  override val run = {
    {
      for {
        _ <- Console.printLine("Certificate Chain TLS Server starting on https://localhost:8443/")
        _ <- Console.printLine("Endpoint:")
        _ <- Console.printLine("  - https://localhost:8443/hello       : Basic hello endpoint")
        _ <- Console.printLine("\nThe server will send the full certificate chain:")
        _ <- Console.printLine("  1. Server Certificate (signed by Intermediate CA)")
        _ <- Console.printLine("  2. Intermediate CA Certificate (signed by Root CA)")
        _ <- Console.printLine("\nPress Ctrl+C to stop...")
      } yield ()
    } *>
      Server.serve(routes).provide(serverConfig, Server.live)
  }

}
