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
  AlpnPolicy,
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
 * Todo 3: the server TLS ALPN protocol list is explicit configuration on
 * [[TlsConfig]] instead of a hardcoded `Array("h2")` inside `TcpListener`.
 *
 * Real TLS handshake proof over loopback [[zio.http.h2.H2Transport]]:
 *   - default `TlsConfig` advertises h2-only and rejects an http/1.1-only
 *     client at the TLS layer (`SSLHandshakeException`, the pre-existing
 *     `StrictH2` behavior);
 *   - `TlsConfig(alpnProtocols = List("h2", "http/1.1"), alpnPolicy =
 *     AlpnPolicy.NegotiateH2Preferred)` negotiates h2 with an h2 client (full
 *     GET round-trip, 200) AND completes a TLS handshake advertising `http/1.1`
 *     with an http/1.1-only client - wire proof the ALPN list came from config.
 *
 * Note: `AlpnPolicy` is the *server-side* acceptance policy only. The server
 * stays H2-only (no HTTP/1.1 fallback handling); the policy controls TLS
 * rejection vs. acceptance. It is independent from any future client-side
 * `ClientAlpnPolicy`.
 */
@experimental
object AlpnConfigSpec extends ZIOSpecDefault {

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
    suite("AlpnConfigSpec")(
      test("TlsConfig defaults advertise h2-only with StrictH2 policy") {
        ZIO.attempt {
          val tlsCfg = TlsConfig(
            certChain = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          assertTrue(
            tlsCfg.alpnProtocols == List("h2"),
            tlsCfg.alpnPolicy == AlpnPolicy.StrictH2,
          )
        }
      },
      test("default config rejects an http/1.1-only client with SSLHandshakeException") {
        val tlsCfg = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
        )
        withTlsServer(tlsCfg) { port =>
          ZIO.attemptBlocking(handshakeOnly(port, Array("http/1.1"))).exit.map { exit =>
            val rejectedAtTlsLayer = exit match {
              case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
              case _                   => false
            }
            assertTrue(exit.isFailure, rejectedAtTlsLayer)
          }
        }
      },
      test("NegotiateH2Preferred with h2+http/1.1 list negotiates h2 with an h2 client and serves 200") {
        val tlsCfg = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
          alpnProtocols = List("h2", "http/1.1"),
          alpnPolicy = AlpnPolicy.NegotiateH2Preferred,
        )
        withTlsServer(tlsCfg) { port =>
          ZIO.attemptBlocking {
            val negotiated = handshakeOnly(port, Array("h2"))
            assertTrue(negotiated == "h2")
          } && ZIO.attemptBlocking {
            val (version, status, _) = jdkGet(port, "/")
            assertTrue(version == java.net.http.HttpClient.Version.HTTP_2, status == 200)
          }
        }
      },
      test("NegotiateH2Preferred advertises http/1.1 from config: http/1.1-only client completes TLS handshake") {
        val tlsCfg = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
          alpnProtocols = List("h2", "http/1.1"),
          alpnPolicy = AlpnPolicy.NegotiateH2Preferred,
        )
        withTlsServer(tlsCfg) { port =>
          ZIO.attemptBlocking {
            // Wire proof the ALPN list came from config: under the default
            // h2-only list this exact handshake fails (previous test), here
            // the server advertises http/1.1 so TLS completes.
            val negotiated = handshakeOnly(port, Array("http/1.1"))
            assertTrue(negotiated == "http/1.1")
          }
        }
      },
    ) @@ sequential

  private def withTlsServer[R](tlsCfg: TlsConfig)(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          new H2Transport(
            Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("alpn-config-ok")))),
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
   * Raw TLS handshake only (no HTTP framing): returns the ALPN protocol the
   * server negotiated. Throws (SSLHandshakeException against a rejecting
   * server) when negotiation fails.
   */
  private def handshakeOnly(port: Int, clientAlpn: Array[String]): String = {
    val rawSocket = new Socket("127.0.0.1", port)
    rawSocket.setSoTimeout(5000)
    val sslSocket = trustAllContext().getSocketFactory
      .createSocket(rawSocket, "127.0.0.1", port, true)
      .asInstanceOf[SSLSocket]
    try {
      val params = sslSocket.getSSLParameters
      params.setApplicationProtocols(clientAlpn)
      sslSocket.setSSLParameters(params)
      sslSocket.setUseClientMode(true)
      sslSocket.startHandshake()
      sslSocket.getApplicationProtocol
    } finally {
      try sslSocket.close()
      catch { case _: Throwable => () }
    }
  }

  private def jdkGet(port: Int, path: String): (java.net.http.HttpClient.Version, Int, String) = {
    val trustAll = Array[TrustManager](new javax.net.ssl.X509ExtendedTrustManager {
      override def checkClientTrusted(c: Array[java.security.cert.X509Certificate], a: String): Unit = ()
      override def checkServerTrusted(c: Array[java.security.cert.X509Certificate], a: String): Unit = ()
      override def checkClientTrusted(
        c: Array[java.security.cert.X509Certificate],
        a: String,
        s: java.net.Socket,
      ): Unit = ()
      override def checkServerTrusted(
        c: Array[java.security.cert.X509Certificate],
        a: String,
        s: java.net.Socket,
      ): Unit = ()
      override def checkClientTrusted(
        c: Array[java.security.cert.X509Certificate],
        a: String,
        e: javax.net.ssl.SSLEngine,
      ): Unit = ()
      override def checkServerTrusted(
        c: Array[java.security.cert.X509Certificate],
        a: String,
        e: javax.net.ssl.SSLEngine,
      ): Unit = ()
      override def getAcceptedIssuers: Array[java.security.cert.X509Certificate]                     = Array.empty
    })
    val ctx      = SSLContext.getInstance("TLS")
    ctx.init(null, trustAll, new java.security.SecureRandom())
    val client   = java.net.http.HttpClient
      .newBuilder()
      .version(java.net.http.HttpClient.Version.HTTP_2)
      .sslContext(ctx)
      .connectTimeout(java.time.Duration.ofSeconds(5))
      .build()
    val request  = java.net.http.HttpRequest
      .newBuilder(java.net.URI.create(s"https://127.0.0.1:$port$path"))
      .timeout(java.time.Duration.ofSeconds(10))
      .GET()
      .build()
    val response =
      client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
    (response.version(), response.statusCode(), response.body())
  }
}
