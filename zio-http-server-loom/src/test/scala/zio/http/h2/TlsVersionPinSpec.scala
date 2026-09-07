package zio.http.h2

import java.net.Socket
import javax.net.ssl.{SSLContext, SSLSocket, TrustManager, X509TrustManager}

import scala.annotation.experimental

import zio._
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.{
  BindAddress,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Protocol,
  Response,
  Route,
  Routes,
  TlsConfig,
  TlsSource,
}

/**
 * Todo 4: the server TLS protocol versions are pinned configuration on
 * [[TlsConfig]] instead of the JDK default, and a caller-provided
 * [[TlsSource.SslContext]] can never silently bypass the configured ALPN list.
 *
 * Real TLS handshake proof over loopback [[zio.http.h2.H2Transport]]:
 *   - default `TlsConfig` pins `List("TLSv1.3", "TLSv1.2")`;
 *   - `TlsConfig(tlsVersions = List("TLSv1.3"))` negotiates TLSv1.3 on the wire
 *     (`SSLSession.getProtocol`) while still negotiating `h2`;
 *   - a TLSv1.2-only client is rejected at the TLS layer
 *     (`SSLHandshakeException`) when the server pins TLSv1.3;
 *   - a raw `TlsSource.SslContext` without ALPN is wrapped with the configured
 *     ALPN list (still negotiates `h2`), and an empty `alpnProtocols` fails
 *     fast with a clear `ALPN not configured on provided SSLContext` error
 *     instead of a silent bypass.
 */
@experimental
object TlsVersionPinSpec extends ZIOSpecDefault {

  private val TestCert =
    """-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIIFmlxlymbftowDQYJKoZIhvcNAQEMBQAwXTELMAkGA1UE
BhMCVVMxDTALBgNVBAgTBFRlc3QxDTALBgNVBAcTBFRlc3QxDTALBgNVBAoTBFRl
c3QxDTALBgNVBAsTBFRlc3QxEjAQBgNVBAMTCWxvY2FsaG9zdDAeFw0yNjA2MzAy
MjA1NTlaFw0yNzA2MzAyMjA1NTlaMF0xCzAJBgNVBAYTAlVTMQ0wCwYDVQQIEwRU
ZXN0MQ0wCwYDVQQHEwRUZXN0MQ0wCwYDVQQKEwRUZXN0MQ0wCwYDVQQLEwRUZXN0
MRIwEAYDVQQDEwlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQDihzLu4ln5ta1Rgac4J3GsWMLWVjMoud5NiZczB7RLHQx+yt1uYhDqc8HT
gzkEBU8lel1IdEMP+m4Y/tVZLrjMaH6lvjbLSQLjdgIsvtqeHmTfMBcCTr9E+r4k
Lhtc1utAOpL18DPBxXEQ7ib2MAtxjLXJQIU/Zh4GJNfbJ69IjFF/PTZUZsIWmJxB
zR9M+2NN1y0gtH6FpdQepQeFaeCJ43652NIKGAuM/w4G2DYSBUsHb/WsMc5QZm0M
DQ6Gy9E76jyghywdkUPw7dnioqzUhbCIZ8eXiL4YbJ6n9eeWvVrGyAWGBYstYEJq
OrR2KbGcd57R3ZvAkPcHIdIy+WO9AgMBAAGjITAfMB0GA1UdDgQWBBT8SbyNOu1I
6jQLbe4/D888GtPHeTANBgkqhkiG9w0BAQwFAAOCAQEAoa31bUJ541BZn321u3K8
XYfdFlTy3zLF4E7OlC9ygepgMKFmntQOnfg19rKgZO+VkQ8kBusgo/jiavjrQIDw
2tTwKel+kN1STaLt5xEWMQsGbGvT7iSejin3wFSxMIrbWKtSOc3Li0AmbdERJ37L
QAYSxLK+vU4BTT/whdI223xeFLQGYFhMyTag09Osw1WLUUZRvLh1FPeV5P5dWpzc
dpBrxWvWGRl2+Nle0zAQNznhfn8ydP/7K3Lv/f3qQ48EgXmSDdhvSUfX24CLVLXk
mETcQb+xmaWObOxkaK0iWGoQWPs2UFKVHPDmUlsYSt0ePGsiu1uEbgWrfr+6vBdx
Wg==
-----END CERTIFICATE-----"""

  private val TestKey =
    """-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDihzLu4ln5ta1R
gac4J3GsWMLWVjMoud5NiZczB7RLHQx+yt1uYhDqc8HTgzkEBU8lel1IdEMP+m4Y
/tVZLrjMaH6lvjbLSQLjdgIsvtqeHmTfMBcCTr9E+r4kLhtc1utAOpL18DPBxXEQ
7ib2MAtxjLXJQIU/Zh4GJNfbJ69IjFF/PTZUZsIWmJxBzR9M+2NN1y0gtH6FpdQe
pQeFaeCJ43652NIKGAuM/w4G2DYSBUsHb/WsMc5QZm0MDQ6Gy9E76jyghywdkUPw
7dnioqzUhbCIZ8eXiL4YbJ6n9eeWvVrGyAWGBYstYEJqOrR2KbGcd57R3ZvAkPcH
IdIy+WO9AgMBAAECggEABLbzpG0pmjzhwpSEOnL3trKSO4vHvM1BhzOZ5gH/CqEs
JWdrfGSmHXsTSaethBvoLcuCLYPd8XMw32xOXHDQf9Cc8i4nTcvTN5C5Mt02B5xy
VQLXN8ET0ge19WLQRvpiIxAVBvFc4meNluCeBvmxA0f+cJXbMBqb/Vy+8Vy+FTBs
a3lthG4BVP7/q2SAUbxFQnajSGYHIW7bMKQUThKEPztffiv3pvws2nmSj3A/Ge99
eBRySh9fE2N46QcfAZ7TRMrU+nR6UpH7aBTL0h3T8qTVn/1HdYQyJBtIqhEVFHYZ
q93JkaZJP8Plhvq5gcnrjG1kLBF+w5Uh5d1l5/RqsQKBgQDzgQmCqYIDxWNE1R/d
zL43DOWc0/xA04vVR9NoFr22Yydzv17u34a21LG/TfH3byDSUqd9luKaNy0pykSD
79Vsycg2Bejk5LiZX/5rrX/OGs17Zi2KrQE6DvXdTQOkSaveU7zVKZKyck7mgreW
wXivdVKfqaTrizx68/y94WsY3wKBgQDuJyVAdO4sHJhQOBfp/wCNVfu/BqfQMJpA
/37dVt7HeiMh9hKx0IMY6iVTCXOoFIR9SpH4uiiw8/WkAfcUld4zuE68ymRTaZY7
EgX4i+ltRrXnp1Ac3FSHu972Z2GIFCqcXJ+Qj4aShw1c3bSSuU2pIDYmrAmcfKtK
tu/SAApq4wKBgCBnLm3Nwrhfvur89WWdhj5rH+7zoqC5xeTWzwIN7KbloO1dLPPa
mOGhghmz9Jv5lMOILjOfLX5aE095VA6+jocQfuz5cllrOklmpcOMbfJuTKO8IBlR
FlW0gfE1+2MUTqOiPwGaq6PFZEx2XpnYGwg2M419lK2ndJ/j8eEOqyK/AoGAGDG9
5Rh8Ads91hh8xXb0lWdA1h1U+x+U7DmIp+/lXhqYayDWsV3fk65l8FOrfk3nT9s9
jSlMbP273NeeRGcdVd/JkAB3xMmbS5D/Lkr4gfOHE2u6BdSUed2qPxotnGeAFLaM
N2F9aHFz+BVF/Qn6S85L8g3URCOeO07uekUqycUCgYBsnu7/9m00ZqP9GUyvVzRr
Br3/KmT2lozjcl/DpalWSKCZIW0lYgKYaiWEc165D4vj/ZBhHO7OPGeT2jqHk4/W
YdZ72W2776kWb4YTEdmJPNwgZIVFzcSZXzn8TKnCnwbHEilz51GFrMH1ZJNvLK3Q
tylLU8iZnM9E7+/GSVghdQ==
-----END PRIVATE KEY-----"""

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("TlsVersionPinSpec")(
      test("TlsConfig defaults pin TLSv1.3 and TLSv1.2") {
        ZIO.attempt {
          val tlsCfg = TlsConfig(
            certChain = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          assertTrue(tlsCfg.tlsVersions == List("TLSv1.3", "TLSv1.2"))
        }
      },
      test("server with tlsVersions=List(TLSv1.3) negotiates TLSv1.3 on the wire") {
        val tlsCfg = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
          tlsVersions = List("TLSv1.3"),
        )
        withTlsServer(tlsCfg) { port =>
          ZIO.attemptBlocking {
            // Wire proof: the negotiated session protocol comes from the
            // server's pinned list, and ALPN still negotiates h2.
            val (sessionProtocol, negotiated) = handshakeWithProtocol(port, Array("h2"), Array("TLSv1.3", "TLSv1.2"))
            assertTrue(sessionProtocol == "TLSv1.3", negotiated == "h2")
          }
        }
      },
      test("TLSv1.2-only client is rejected when server pins TLSv1.3") {
        val tlsCfg = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
          tlsVersions = List("TLSv1.3"),
        )
        withTlsServer(tlsCfg) { port =>
          ZIO
            .attemptBlocking(handshakeWithProtocol(port, Array("h2"), Array("TLSv1.2")))
            .exit
            .map { exit =>
              val rejectedAtTlsLayer = exit match {
                case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
                case _                   => false
              }
              assertTrue(exit.isFailure, rejectedAtTlsLayer)
            }
        }
      },
      test("raw SslContext without ALPN is wrapped with the configured ALPN list") {
        ZIO.attempt {
          val pemCfg = TlsConfig(
            certChain = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          // A caller-provided context carries key material but no ALPN: the
          // server must still apply TlsConfig.alpnProtocols per socket.
          val raw    = TcpListener.createSslContext(pemCfg)
          assertTrue(raw.getDefaultSSLParameters.getApplicationProtocols.isEmpty)
          val tlsCfg = TlsConfig(
            certChain = TlsSource.SslContext(raw),
            privateKey = TlsSource.SslContext(raw),
          )
          tlsCfg
        }.flatMap { tlsCfg =>
          withTlsServer(tlsCfg) { port =>
            ZIO.attemptBlocking {
              val (_, negotiated) = handshakeWithProtocol(port, Array("h2"), Array("TLSv1.3", "TLSv1.2"))
              assertTrue(negotiated == "h2")
            }
          }
        }
      },
      test("raw SslContext with empty alpnProtocols fails fast with a clear ALPN error") {
        ZIO.attempt(TcpListener.createSslContext(emptyAlpnTlsCfg())).exit.map { exit =>
          val failsFast = exit match {
            case Exit.Failure(cause) =>
              cause.failures.exists {
                case e: IllegalArgumentException =>
                  e.getMessage.contains("ALPN not configured on provided SSLContext")
                case _                           => false
              }
            case _                   => false
          }
          assertTrue(exit.isFailure, failsFast)
        }
      },
    ) @@ sequential

  private def emptyAlpnTlsCfg(): TlsConfig = {
    val raw = SSLContext.getInstance("TLS")
    raw.init(null, null, new java.security.SecureRandom())
    TlsConfig(
      certChain = TlsSource.SslContext(raw),
      privateKey = TlsSource.SslContext(raw),
      alpnProtocols = Nil,
    )
  }

  private def withTlsServer[R](tlsCfg: TlsConfig)(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          new H2Transport(
            Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("tls-pin-ok")))),
            Context.empty,
            Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
            DefectHandler.default,
          ).start()
        },
      )(handle => ZIO.succeed(handle.close0()))
      .flatMap { handle =>
        val port = handle.binding.address match {
          case BoundAddress.Tcp(_, thePort) => thePort
          case other                        => throw new AssertionError("Expected TCP: " + other)
        }
        use(port)
      }

  private def trustAllContext(): SSLContext = {
    val trustAll = Array[TrustManager](new X509TrustManager {
      override def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def getAcceptedIssuers: Array[java.security.cert.X509Certificate] = Array.empty
    })
    val ctx      = SSLContext.getInstance("TLS")
    ctx.init(null, trustAll, new java.security.SecureRandom())
    ctx
  }

  /**
   * Raw TLS handshake only (no HTTP framing): returns the negotiated TLS
   * session protocol and the negotiated ALPN protocol. Throws
   * (SSLHandshakeException against a rejecting server) when negotiation fails.
   */
  private def handshakeWithProtocol(
    port: Int,
    clientAlpn: Array[String],
    clientVersions: Array[String],
  ): (String, String) = {
    val rawSocket = new Socket("127.0.0.1", port)
    rawSocket.setSoTimeout(5000)
    val sslSocket = trustAllContext().getSocketFactory
      .createSocket(rawSocket, "127.0.0.1", port, true)
      .asInstanceOf[SSLSocket]
    try {
      val params = sslSocket.getSSLParameters
      params.setApplicationProtocols(clientAlpn)
      params.setProtocols(clientVersions)
      sslSocket.setSSLParameters(params)
      sslSocket.setUseClientMode(true)
      sslSocket.startHandshake()
      (sslSocket.getSession.getProtocol, sslSocket.getApplicationProtocol)
    } finally {
      try sslSocket.close()
      catch { case _: Throwable => () }
    }
  }
}
