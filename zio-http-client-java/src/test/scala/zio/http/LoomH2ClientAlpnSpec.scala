package zio.http

import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager

import scala.annotation.experimental

import zio._
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._
import zio.http.h2.H2Transport

/**
 * Todo 14: the Loom H2 client driver negotiates per [[ClientAlpnPolicy]].
 *
 * Real-socket proof (ephemeral ports, `acquireRelease` everywhere):
 *   - H2C prior-knowledge GET against a plaintext LoomServer: 200 over HTTP/2.0
 *     (H2C without TLS works);
 *   - TLS GET against a StrictH2 LoomServer: 200 over HTTP/2.0;
 *   - TLS GET against a cert with a WRONG hostname (127.0.0.1 vs the
 *     `CN=localhost` test cert, real trust): fast
 *     [[javax.net.ssl.SSLException]] proving hostname verification is active,
 *     never a silent 200;
 *   - H2PreferredWithH11Fallback against an h1.1-only TLS endpoint: falls back
 *     to the JDK h1.1 leg, 200 over HTTP/1.1;
 *   - StrictH2 against an h1.1-only TLS endpoint: fast
 *     [[javax.net.ssl.SSLHandshakeException]], never a silent 200 fallback;
 *   - H2Preferred against an unknown-ALPN TLS endpoint: fast failure, never a
 *     silent 200;
 *   - connect timeout fires instead of hanging.
 */
@experimental
object LoomH2ClientAlpnSpec extends ZIOSpecDefault {

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
    suite("LoomH2ClientAlpnSpec")(
      test("H2C prior-knowledge GET against a plaintext LoomServer returns 200 over HTTP/2") {
        withPlainH2cServer { port =>
          ZIO.attemptBlocking {
            val driver   = LoomH2ClientDriver.default
            val response = driver.send(Request.get(absUrl(s"http://127.0.0.1:$port/")))
            val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
            proof(s"h2c negotiated=${response.version} status=${response.status} body=$body")
            assertTrue(
              response.version == Version.`HTTP/2.0`,
              response.status == Status.Ok,
              body == "loom-h2c-ok",
            )
          }
        }
      },
      test("TLS GET against a StrictH2 LoomServer negotiates h2 and returns 200") {
        withStrictH2Server { port =>
          ZIO.attemptBlocking {
            val config   = ClientConfig(alpn = ClientAlpnPolicy.StrictH2)
            val driver   = LoomH2ClientDriver(config, trustAllSslContext())
            val response = driver.send(Request.get(absUrl(s"https://127.0.0.1:$port/")))
            val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
            proof(s"tls-h2 negotiated=${response.version} status=${response.status} body=$body")
            assertTrue(
              response.version == Version.`HTTP/2.0`,
              response.status == Status.Ok,
              body == "loom-h2-ok",
            )
          }
        }
      },
      test("TLS GET against a cert with a wrong hostname fails fast (hostname verification active)") {
        withStrictH2Server { port =>
          ZIO.attemptBlocking {
            // Real trust for the CN=localhost test cert, but the request targets
            // 127.0.0.1: with endpoint identification the handshake must reject
            // the hostname mismatch instead of returning a silent 200.
            val config = ClientConfig(alpn = ClientAlpnPolicy.StrictH2)
            val driver = LoomH2ClientDriver(config, certTrustingSslContext())
            driver.send(Request.get(absUrl(s"https://127.0.0.1:$port/")))
          }.exit.map { exit =>
            val hostnameRejected = exit match {
              case Exit.Failure(cause) =>
                cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException]) ||
                cause.defects.exists(_.isInstanceOf[javax.net.ssl.SSLException])
              case _                   => false
            }
            proof(s"wrong-hostname failed=${exit.isFailure} sslFailure=$hostnameRejected")
            assertTrue(exit.isFailure, hostnameRejected)
          }
        }
      },
      test("H2PreferredWithH11Fallback falls back to the JDK h1.1 leg against an h1.1-only TLS endpoint") {
        withH11StubServer(List("http/1.1")) { port =>
          ZIO.attemptBlocking {
            val driver   = LoomH2ClientDriver(ClientConfig(), trustAllSslContext())
            val response = driver.send(Request.get(absUrl(s"https://127.0.0.1:$port/")))
            val body     = new String(response.body.toArray, StandardCharsets.UTF_8)
            proof(s"h11-fallback negotiated=${response.version} status=${response.status} body=$body")
            assertTrue(
              response.version == Version.`HTTP/1.1`,
              response.status == Status.Ok,
              body == "h11-fallback-ok",
            )
          }
        }
      },
      test("StrictH2 against an h1.1-only TLS endpoint fails fast with SSLHandshakeException, never silent 200") {
        withH11StubServer(List("http/1.1")) { port =>
          ZIO.attemptBlocking {
            val config = ClientConfig(alpn = ClientAlpnPolicy.StrictH2)
            val driver = LoomH2ClientDriver(config, trustAllSslContext())
            driver.send(Request.get(absUrl(s"https://127.0.0.1:$port/")))
          }.exit.map { exit =>
            val fastHandshakeFailure = exit match {
              case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
              case _                   => false
            }
            proof(s"strict-vs-h11 failed=${exit.isFailure} sslFailure=$fastHandshakeFailure")
            assertTrue(exit.isFailure, fastHandshakeFailure)
          }
        }
      },
      test("H2Preferred against an unknown-ALPN TLS endpoint fails fast, never silent 200") {
        withH11StubServer(List("weird-proto")) { port =>
          ZIO.attemptBlocking {
            val driver = LoomH2ClientDriver(ClientConfig(), trustAllSslContext())
            driver.send(Request.get(absUrl(s"https://127.0.0.1:$port/")))
          }.exit.map { exit =>
            val fastFailure = exit match {
              case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
              case _                   => false
            }
            proof(s"unknown-alpn failed=${exit.isFailure} sslFailure=$fastFailure")
            assertTrue(exit.isFailure, fastFailure)
          }
        }
      },
      test("connect timeout fires instead of hanging") {
        ZIO.attemptBlocking {
          val config = ClientConfig(
            deadline = DeadlineConfig(connectTimeout = Some(java.time.Duration.ofMillis(500))),
          )
          val driver = LoomH2ClientDriver(config)
          val start  = java.lang.System.currentTimeMillis()
          try {
            driver.send(Request.get(absUrl("http://192.0.2.1:81/")))
            -1L
          } catch {
            case _: Throwable => java.lang.System.currentTimeMillis() - start
          }
        }.map { elapsed =>
          proof(s"connect-timeout elapsedMs=$elapsed")
          assertTrue(elapsed >= 0L, elapsed < 15000L)
        }
      },
    ) @@ sequential

  private def absUrl(raw: String): URL =
    URL.parse(raw).fold(err => throw new IllegalArgumentException("Invalid test URL: " + err), identity)

  private def proof(line: String): Unit =
    println(s">> [LoomH2ClientAlpnSpec] $line")

  private def withPlainH2cServer[R](use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          new H2Transport(
            Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("loom-h2c-ok")))),
            Context.empty,
            Connector(bind = BindAddress.localhost(0)),
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

  private def withStrictH2Server[R](use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val tlsCfg = TlsConfig(
            certChain = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          new H2Transport(
            Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("loom-h2-ok")))),
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

  /**
   * Minimal h1.1-only TLS endpoint stub (test-only): raw TLS socket offering
   * exactly `serverAlpn`, then plain HTTP/1.1 framing. Lets the spec prove the
   * *client's* ALPN policy without depending on any HTTP/1.1 server.
   */
  private def withH11StubServer[R](serverAlpn: List[String])(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val serverSocket = new ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
          val running      = new AtomicBoolean(true)
          val acceptor     = Thread.ofVirtual().name("loom-h2client-alpn-stub").start { () =>
            while (running.get()) {
              try {
                val raw = serverSocket.accept()
                Thread.ofVirtual().name("loom-h2client-alpn-stub-conn").start(() => handleStubConn(raw, serverAlpn))
              } catch {
                case _: java.net.SocketException => ()
              }
            }
          }
          (serverSocket, running, acceptor)
        },
      ) { case (serverSocket, running, acceptor) =>
        ZIO.succeed {
          running.set(false)
          try serverSocket.close()
          catch { case _: Throwable => () }
          acceptor.join(5000L)
        }
      }
      .flatMap { case (serverSocket, _, _) => use(serverSocket.getLocalPort) }

  private def handleStubConn(raw: Socket, serverAlpn: List[String]): Unit = {
    raw.setSoTimeout(10000)
    val tls = serverSslContext().getSocketFactory
      .createSocket(raw, "127.0.0.1", raw.getPort, true)
      .asInstanceOf[SSLSocket]
    try {
      val params = tls.getSSLParameters
      params.setApplicationProtocols(serverAlpn.toArray)
      tls.setSSLParameters(params)
      tls.setUseClientMode(false)
      tls.startHandshake()
      val input  = tls.getInputStream
      readHttp1Request(input)
      val body   = "h11-fallback-ok".getBytes(StandardCharsets.UTF_8)
      val head   =
        s"HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
      val out    = tls.getOutputStream
      out.write(head.getBytes(StandardCharsets.US_ASCII))
      out.write(body)
      out.flush()
    } catch {
      case _: Throwable => ()
    } finally {
      try tls.close()
      catch { case _: Throwable => () }
    }
  }

  private def readHttp1Request(input: java.io.InputStream): Unit = {
    val seen    = new Array[Byte](65536)
    var total   = 0
    var matched = 0
    // Match the header terminator \r\n\r\n without retaining the request.
    val needle  = Array[Byte]('\r'.toByte, '\n'.toByte, '\r'.toByte, '\n'.toByte)
    var done    = false
    while (!done && total < seen.length) {
      val read = input.read(seen, total, seen.length - total)
      if (read < 0) done = true
      else {
        total += read
        var i = total - read - math.min(matched, 3)
        if (i < 0) i = 0
        while (i < total && !done) {
          if (seen(i) == needle(matched)) {
            matched += 1
            if (matched == needle.length) done = true
          } else if (seen(i) == '\r'.toByte) matched = 1
          else matched = 0
          i += 1
        }
      }
    }
  }

  private def trustAllSslContext(): SSLContext = {
    val trustAll = Array[TrustManager](new X509ExtendedTrustManager {
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String): Unit                 = ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String): Unit                 = ()
      override def checkClientTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = ()
      override def checkServerTrusted(chain: Array[X509Certificate], authType: String, socket: Socket): Unit = ()
      override def checkClientTrusted(
        chain: Array[X509Certificate],
        authType: String,
        e: javax.net.ssl.SSLEngine,
      ): Unit =
        ()
      override def checkServerTrusted(
        chain: Array[X509Certificate],
        authType: String,
        e: javax.net.ssl.SSLEngine,
      ): Unit =
        ()
      override def getAcceptedIssuers: Array[X509Certificate] = Array.empty
    })
    val ctx      = SSLContext.getInstance("TLS")
    ctx.init(null, trustAll, new SecureRandom())
    ctx
  }

  private def certTrustingSslContext(): SSLContext = {
    val certBytes = TestCert.getBytes(StandardCharsets.UTF_8)
    val certs     =
      CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(certBytes))
    val store     = KeyStore.getInstance(KeyStore.getDefaultType)
    store.load(null, Array.emptyCharArray)
    var index     = 0
    val it        = certs.iterator()
    while (it.hasNext) {
      store.setCertificateEntry("test-cert-" + index, it.next())
      index += 1
    }
    val tmf       = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(store)
    val ctx       = SSLContext.getInstance("TLS")
    ctx.init(null, tmf.getTrustManagers, new SecureRandom())
    ctx
  }

  private def serverSslContext(): SSLContext = {
    val certBytes = TestCert.getBytes(StandardCharsets.UTF_8)
    val certs     =
      CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(certBytes))
    val keyBytes  = Base64.getDecoder.decode(
      TestKey.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", ""),
    )
    val key       = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes))
    val store     = KeyStore.getInstance(KeyStore.getDefaultType)
    store.load(null, Array.emptyCharArray)
    val certArray = certs.toArray(new Array[java.security.cert.Certificate](0))
    store.setKeyEntry("stub", key, Array.emptyCharArray, certArray)
    val kmf       = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(store, Array.emptyCharArray)
    val ctx       = SSLContext.getInstance("TLS")
    ctx.init(kmf.getKeyManagers, null, new SecureRandom())
    ctx
  }
}
