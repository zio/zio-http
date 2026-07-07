package zio.http.h2

import java.net.URI
import java.net.http.HttpClient.Version
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.{SSLContext, SSLEngine, TrustManager, X509ExtendedTrustManager}

import scala.annotation.experimental

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Protocol,
  Response,
  Route,
  Routes,
  Status,
  TlsConfig,
  TlsSource,
}

/**
 * Regression coverage using a genuinely independent HTTP/2 client
 * implementation - the JDK's own `java.net.http.HttpClient` - instead of this
 * repo's own `FrameCodec`/`Hpack` based test clients (`RawH2Client` /
 * `TlsH2Client` used everywhere else in this suite).
 *
 * Every other H2 test in this module builds its client from the SAME codec used
 * by the server, so a subtly wrong wire encoding that happens to "round-trip"
 * correctly against itself would never be caught by them. That is exactly the
 * class of bug (RFC 9113 violations such as `SETTINGS_ENABLE_PUSH=1`, mux
 * frame-delivery races, and missing stream-level flow-control wiring) that real
 * `curl`/`nghttp2`/`tshark` captures caught during this investigation, but the
 * hand-rolled clients did not, for a long time. This spec makes that
 * independent verification permanent and CI-enforced instead of ephemeral.
 *
 * `java.net.http.HttpClient` only negotiates HTTP/2 via TLS ALPN (it does not
 * expose "prior knowledge" h2c), so this spec runs the real engine over
 * `Protocol.H2` with a throwaway self-signed localhost certificate - the same
 * one used by `H2TlsSpec` - and a trust-all `SSLContext` with hostname
 * verification disabled (the client dials the literal loopback IP, which does
 * not match the certificate's `CN=localhost`, and this is a
 * deliberately-insecure test-only certificate rather than a real trust
 * boundary).
 */
@experimental
object H2IndependentClientSpec extends ZIOSpecDefault {

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
    suite("H2IndependentClientSpec")(
      test("JDK java.net.http.HttpClient (real HTTP/2, ALPN h2) GET receives a genuine 200 response") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("independent-client-ok"))))
        withTlsServer(routes) { port =>
          ZIO.attemptBlocking {
            val (version, status, body) = jdkGet(port, "/")
            assertTrue(version == Version.HTTP_2, status == 200, body == "independent-client-ok")
          }
        }
      },
      // Response body spans several DATA frames (default maxFrameSize is 16384 bytes) while
      // staying under the default flow-control window, exercising real multi-frame DATA
      // reassembly end-to-end through a genuinely independent client.
      test("JDK java.net.http.HttpClient correctly reassembles a response spanning multiple DATA frames") {
        val bodyBytes = Chunk.fromArray(Array.tabulate(40000)(i => (i % 256).toByte))
        val routes    = Routes(
          Route(RoutePattern.GET, Handler.succeed(Response(status = Status.Ok, body = Body.fromChunk(bodyBytes)))),
        )
        withTlsServer(routes) { port =>
          ZIO.attemptBlocking {
            val (version, status, bodyString) = jdkGet(port, "/")
            val expected                      = new String(bodyBytes.toArray, "ISO-8859-1")
            assertTrue(version == Version.HTTP_2, status == 200, bodyString == expected)
          }
        }
      },
    ) @@ sequential

  private def withTlsServer[R](routes: Routes[Any])(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val tlsCfg = TlsConfig(
            certChain = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          new H2Transport(
            routes,
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

  // Extends X509ExtendedTrustManager (not the legacy X509TrustManager) so the JDK does not
  // wrap it in its own AbstractTrustManagerWrapper, which performs hostname/identity checks
  // unconditionally regardless of SSLParameters.endpointIdentificationAlgorithm. Implementing
  // the extended interface directly means checkServerTrusted fully controls trust, matching
  // the deliberate test-only relaxation documented on `newHttpClient` below.
  private def trustAllSslContext(): SSLContext = {
    val trustAll = Array[TrustManager](new X509ExtendedTrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit = ()
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String, socket: java.net.Socket): Unit =
        ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String, socket: java.net.Socket): Unit =
        ()
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String, engine: SSLEngine): Unit = ()
      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    })
    val ctx      = SSLContext.getInstance("TLS")
    ctx.init(null, trustAll, new java.security.SecureRandom())
    ctx
  }

  // This test dials the literal loopback IP, which does not match the throwaway test
  // certificate's `CN=localhost` (and has no SAN entries). Combined with the trust-all
  // SSLContext above, skipping identity checks is a deliberate test-only relaxation of TLS
  // trust - it does not weaken anything the HTTP/2 wire-protocol assertions below verify.
  // HttpClient.Builder.sslParameters(...) is deliberately avoided: supplying a custom
  // SSLParameters replaces the ALPN protocol list HttpClient otherwise sets up itself,
  // causing ALPN to negotiate no protocol at all. Identity checks are skipped exclusively via
  // the X509ExtendedTrustManager above instead.
  private def newHttpClient(): HttpClient =
    HttpClient
      .newBuilder()
      .version(Version.HTTP_2)
      .sslContext(trustAllSslContext())
      .connectTimeout(Duration.ofSeconds(5))
      .build()

  private def jdkGet(port: Int, path: String): (Version, Int, String) = {
    val client   = newHttpClient()
    val request  = HttpRequest
      .newBuilder(URI.create(s"https://127.0.0.1:$port$path"))
      .timeout(Duration.ofSeconds(10))
      .GET()
      .build()
    val response =
      client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.ISO_8859_1))
    (response.version(), response.statusCode(), response.body())
  }
}
