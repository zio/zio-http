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
    _             <- displayChainVerificationExplanation()
  } yield ()

  def displayChainVerificationExplanation(): Task[Unit] =
    Console.printLine {
      """
Certificate Chain Verification Process:
=====================================
1. Server sends its certificate chain:
   - Server Certificate (CN=localhost)
   - Intermediate CA Certificate

2. Client verifies the chain:
   ✓ Server cert is signed by Intermediate CA
   ✓ Intermediate CA is signed by Root CA
   ✓ Root CA is in client's truststore (trusted)

Trust Chain Path:
┌─ Root CA (in client truststore)
│   CN=Root CA, OU=Security, O=RootCA
│
└─> Intermediate CA (received from server)
    CN=Intermediate CA, OU=Security, O=IntermediateCA
    │
    └─> Server Certificate (received from server)
        CN=localhost, OU=IT, O=MyCompany

Key Points:
- Client only needs Root CA in truststore
- Server provides intermediate certificates
- Full chain is validated automatically
      """
    }

  override val run = app.provide(
    ZLayer.succeed {
      ZClient.Config.default.ssl(
        ClientSSLConfig.FromTrustStoreResource(
          trustStorePath = "certs/tls/intermediate-ca-signed/truststore.p12",
          trustStorePassword = "trustpass",
        ),
      )
    },
    ZLayer.succeed(NettyConfig.default),
    DnsResolver.default,
    ZClient.live,
  )
}
