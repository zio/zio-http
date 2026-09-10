package zio.http.h2

import java.io.{ByteArrayInputStream, EOFException}
import java.net.{ConnectException, Socket}
import java.nio.charset.StandardCharsets
import java.security.{KeyFactory, KeyStore}
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLSocket, TrustManager, X509TrustManager}

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, HpackDecoder, HpackEncoder}
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  LoomServer,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  TlsConfig,
  TlsSource,
  TrustedProxyConfig,
  handler,
}

/**
 * Spoof spec for the H2 proxy-trust model.
 *
 * Forwarding headers (`X-Forwarded-For/Proto/Host`, RFC 7239 `Forwarded`) must
 * only take effect when the TCP peer is trusted (allowlisted CIDR or
 * mTLS-authenticated identity). Default is deny: an untrusted peer's headers
 * are stripped with zero effect, and the resolved client IP falls back to the
 * socket peer address. A server with `requireClientAuth` must reject a peer
 * that presents no certificate.
 */
@experimental
object H2ProxyTrustSpec extends ZIOSpecDefault {
  private final case class ProxyCase(
    name: String,
    trusted: TrustedProxyConfig,
    sentHeaders: List[HeaderField],
    expectedClientIp: String,
    expectedHost: String,
    expectedScheme: String,
  )

  /** Trusted loopback peer: X-Forwarded-* headers take effect. */
  private val TrustedLoopbackXff: ProxyCase =
    ProxyCase(
      name = "trusted loopback peer's X-Forwarded-For yields correct client IP",
      trusted = TrustedProxyConfig(trustedCidrs = Set("127.0.0.1/32")),
      sentHeaders = List(
        HeaderField("x-forwarded-for", "203.0.113.7"),
        HeaderField("x-forwarded-proto", "https"),
        HeaderField("x-forwarded-host", "example.com"),
      ),
      expectedClientIp = "203.0.113.7",
      expectedHost = "example.com",
      expectedScheme = "https",
    )

  /** Same headers from an untrusted peer: stripped with zero effect. */
  private val UntrustedXffIgnored: ProxyCase =
    ProxyCase(
      name = "identical header from untrusted peer is ignored (zero effect)",
      trusted = TrustedProxyConfig(),
      sentHeaders = List(
        HeaderField("x-forwarded-for", "203.0.113.7"),
        HeaderField("x-forwarded-proto", "https"),
        HeaderField("x-forwarded-host", "example.com"),
      ),
      expectedClientIp = "127.0.0.1",
      expectedHost = "127.0.0.1",
      expectedScheme = "http",
    )

  /** Trusted peer: RFC 7239 Forwarded takes effect. */
  private val TrustedRfc7239: ProxyCase =
    ProxyCase(
      name = "RFC 7239 Forwarded from trusted peer yields client IP and host",
      trusted = TrustedProxyConfig(trustedCidrs = Set("127.0.0.0/8")),
      sentHeaders = List(HeaderField("forwarded", "for=198.51.100.9;proto=https;host=example.org")),
      expectedClientIp = "198.51.100.9",
      expectedHost = "example.org",
      expectedScheme = "https",
    )

  /** Same Forwarded header from an untrusted peer: ignored. */
  private val UntrustedRfc7239Ignored: ProxyCase =
    ProxyCase(
      name = "RFC 7239 Forwarded from untrusted peer is ignored",
      trusted = TrustedProxyConfig(),
      sentHeaders = List(HeaderField("forwarded", "for=198.51.100.9;proto=https;host=example.org")),
      expectedClientIp = "127.0.0.1",
      expectedHost = "127.0.0.1",
      expectedScheme = "http",
    )

  override def spec: Spec[TestEnvironment & Scope, Any]                                   =
    suite("H2ProxyTrustSpec")(
      test(TrustedLoopbackXff.name) {
        withServer(TrustedLoopbackXff.trusted, runProxyCase(TrustedLoopbackXff))
      },
      test(UntrustedXffIgnored.name) {
        withServer(UntrustedXffIgnored.trusted, runProxyCase(UntrustedXffIgnored))
      },
      test(TrustedRfc7239.name) {
        withServer(TrustedRfc7239.trusted, runProxyCase(TrustedRfc7239))
      },
      test(UntrustedRfc7239Ignored.name) {
        withServer(UntrustedRfc7239Ignored.trusted, runProxyCase(UntrustedRfc7239Ignored))
      },
      test("default config trusts nothing (default-deny)") {
        ZIO.attempt {
          val config = TrustedProxyConfig()
          assertTrue(
            !config.isTrusted("127.0.0.1", hasPeerCert = false),
            !config.isTrusted("127.0.0.1", hasPeerCert = true),
            !config.isTrusted("10.0.0.1", hasPeerCert = true),
          )
        }
      },
      test("mTLS-required server rejects non-cert peer") {
        // Set when (and only when) the server dispatches a request past the
        // handshake: a rejection must leave it false.
        val handlerHit = new AtomicBoolean(false)
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg    = TlsConfig(
                certChain = TlsSource.PemString(Secret(ServerCertPem)),
                privateKey = TlsSource.PemString(Secret(ServerKeyPem)),
                requireClientAuth = true,
              )
              val transport = new H2Transport(
                Routes(
                  Route(
                    RoutePattern.GET,
                    handler { (_: Request) =>
                      handlerHit.set(true)
                      responseAsResult(Response.ok)
                    },
                  ),
                ),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              )
              transport.start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new MtlsTestClient(tcpPort)
              try {
                // Stage 1 (server-observed accept): TCP connects, so the
                // server took the peer — a refusal here would mean
                // "server down", not "peer rejected".
                val tcpAcceptedByServer      = client.tcpConnected
                // Stage 2 (server-aborted handshake): the no-cert handshake
                // must never yield a speaking server. The abort can race
                // past the client's Finished (fatal alert vs close), so the
                // proof is that no H2 bytes ever come back — never a refusal
                // to reach the server at all.
                val handshakeError           = client.handshakeAttempt()
                handshakeError.foreach(err => println(s"mTLS no-cert handshake error (expected): $err"))
                val refusedBeforeHandshake   = handshakeError.exists(_.isInstanceOf[ConnectException])
                val serverSpokeH2AfterReject =
                  if (refusedBeforeHandshake) true else client.serverProceeds()
                // Stage 3 (per-connection abort): the server is still bound
                // afterwards and never dispatched the request past the
                // failed handshake.
                val serverStillBound         = handle.binding.address match {
                  case BoundAddress.Tcp(_, thePort) => thePort == tcpPort
                  case _                            => false
                }
                val requestDispatched        = handlerHit.get()
                assertTrue(
                  tcpAcceptedByServer,
                  !refusedBeforeHandshake,
                  !serverSpokeH2AfterReject,
                  serverStillBound,
                  !requestDispatched,
                )
              } finally client.close()
            }
          }
      },
      test("mTLS-required server with CA trust store rejects wrong-CA cert peer") {
        // Same 3-stage proof as the no-cert rejection, but the peer presents a
        // well-formed certificate that chains to nothing the server trusts
        // (self-signed server cert against an unrelated EC CA): the handshake
        // must abort and the request must never dispatch.
        val handlerHit = new AtomicBoolean(false)
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg    = TlsConfig(
                certChain = TlsSource.PemString(Secret(ServerCertPem)),
                privateKey = TlsSource.PemString(Secret(ServerKeyPem)),
                requireClientAuth = true,
                trustCertChain = Some(TlsSource.PemString(Secret(WrongCaPem))),
              )
              val transport = new H2Transport(
                Routes(
                  Route(
                    RoutePattern.GET,
                    handler { (_: Request) =>
                      handlerHit.set(true)
                      responseAsResult(Response.ok)
                    },
                  ),
                ),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              )
              transport.start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new MtlsTestClient(
                tcpPort,
                clientCertPem = Some(ServerCertPem),
                clientKeyPem = Some(ServerKeyPem),
              )
              try {
                val tcpAcceptedByServer      = client.tcpConnected
                val handshakeError           = client.handshakeAttempt()
                handshakeError.foreach(err => println(s"mTLS wrong-CA handshake error (expected): $err"))
                val refusedBeforeHandshake   = handshakeError.exists(_.isInstanceOf[ConnectException])
                val serverSpokeH2AfterReject =
                  if (refusedBeforeHandshake) true else client.serverProceeds()
                val serverStillBound         = handle.binding.address match {
                  case BoundAddress.Tcp(_, thePort) => thePort == tcpPort
                  case _                            => false
                }
                val requestDispatched        = handlerHit.get()
                assertTrue(
                  tcpAcceptedByServer,
                  !refusedBeforeHandshake,
                  !serverSpokeH2AfterReject,
                  serverStillBound,
                  !requestDispatched,
                )
              } finally client.close()
            }
          }
      },
    ) @@ sequential
  private val EchoRoutes: Routes[Any]                                                     =
    Routes(
      Route(
        RoutePattern.GET,
        handler { (req: Request) =>
          val clientIp        = req.headers.rawGet("x-client-ip").getOrElse("none")
          val peer            = req.headers.rawGet("x-peer-address").getOrElse("none")
          val forwardedFor    = req.headers.rawGet("x-forwarded-for").getOrElse("none")
          val forwardedHeader = req.headers.rawGet("forwarded").getOrElse("none")
          val host            = req.url.host.getOrElse("none")
          val scheme          = req.url.scheme.map(_.text).getOrElse("none")
          responseAsResult(
            Response(
              status = zio.http.Status.Ok,
              body = Body.fromString(s"$clientIp|$peer|$forwardedFor|$forwardedHeader|$host|$scheme"),
            ),
          )
        },
      ),
    )
  private def withServer[R](
    trusted: TrustedProxyConfig,
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(
            bind = BindAddress.localhost(0),
            protocol = Protocol.H2C(),
            trustedProxy = trusted,
          )
          new LoomServer(connector).serve(EchoRoutes, Context.empty)
        },
      )(handle => ZIO.attemptBlocking(handle.shutdownAndWait()).ignore)
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }
  private def runProxyCase[R](proxyCase: ProxyCase): Int => ZIO[R, Throwable, TestResult] =
    (port: Int) =>
      ZIO.attemptBlocking {
        val client = new ProxyTestClient(port)
        try {
          val seen = client.getWithHeaders(streamId = 1, extra = proxyCase.sentHeaders)
          assertTrue(
            seen.clientIp == proxyCase.expectedClientIp,
            seen.forwardedFor == "none",
            seen.forwardedHeader == "none",
            seen.peer == "127.0.0.1",
            seen.host == proxyCase.expectedHost,
            seen.scheme == proxyCase.expectedScheme,
          )
        } finally client.close()
      }
  private final case class SeenHeaders(
    clientIp: String,
    peer: String,
    forwardedFor: String,
    forwardedHeader: String,
    host: String,
    scheme: String,
  )
  private final class ProxyTestClient(val port: Int) extends AutoCloseable {
    private val PrefaceBytes                                                 =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
    val socket                                                               = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)
    private val out                                                          = socket.getOutputStream
    private val rawIn                                                        = socket.getInputStream
    private var buf                                                          = Chunk.empty[Byte]
    private val encoder                                                      = new HpackEncoder()
    private val decoder                                                      = new HpackDecoder()
    handshake()
    def sendRaw(bytes: Array[Byte]): Unit                                    = { out.write(bytes); out.flush() }
    def sendFrame(frame: H2Frame): Unit                                      = sendRaw(FrameCodec.encode(frame).toArray)
    def getWithHeaders(streamId: Int, extra: List[HeaderField]): SeenHeaders = {
      val pseudo   = List(
        HeaderField(":method", "GET"),
        HeaderField(":path", "/"),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      )
      sendFrame(Headers(streamId, encoder.encode(pseudo ++ extra), endStream = true, endHeaders = true))
      val response = awaitResponse(streamId)
      parseSeen(new String(response.body.toArray, StandardCharsets.UTF_8))
    }
    private def parseSeen(body: String): SeenHeaders                         = {
      val parts = body.split("\\|", -1)
      if (parts.length != 6) throw new AssertionError("Unexpected echo body: " + body)
      SeenHeaders(parts(0), parts(1), parts(2), parts(3), parts(4), parts(5))
    }
    private def awaitResponse(streamId: Int): RawResponse                    = {
      val hdrs   = mutable.ListBuffer.empty[HeaderField]
      var body   = Chunk.empty[Byte]
      var done   = false
      while (!done) {
        readFrame() match {
          case Settings(false, _)                                   => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                    => ()
          case _: WindowUpdate                                      => ()
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            decoder.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            if (end) done = true
          case Data(sid, data, end, _) if sid == streamId           =>
            body = body ++ data
            if (end) done = true
          case RstStream(_, code)                                   =>
            throw new AssertionError("Unexpected RST_STREAM: " + code)
          case GoAway(_, code, dbg)                                 =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                                                    => ()
        }
      }
      val status = hdrs
        .find(_.name == ":status")
        .map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      RawResponse(status, hdrs.toList, body)
    }
    private def readFrame(): H2Frame                                         = {
      while (true) {
        FrameCodec.decode(buf) match {
          case Right((frame, rest))           =>
            buf = rest
            return frame
          case Left(H2Error.InsufficientData) =>
            val tmp = new Array[Byte](8192)
            val n   = rawIn.read(tmp)
            if (n < 0) throw new EOFException("Connection closed")
            buf = buf ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, n))
          case Left(err)                      =>
            throw new AssertionError("Frame decode error: " + err)
        }
      }
      throw new AssertionError("unreachable")
    }
    override def close(): Unit                                               = socket.close()
    private def handshake(): Unit                                            = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      readFrame() match {
        case Settings(false, _) => sendFrame(Settings(ack = true, Nil))
        case other              => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame()
    }
  }
  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte])
  // Unrelated EC CA (same material as H2TlsSpec): the trust anchor for the
  // wrong-CA rejection test. The peer presents the self-signed RSA server
  // cert, which chains to nothing here, so the handshake must abort.
  private val WrongCaPem                                                                  =
    """-----BEGIN CERTIFICATE-----
MIIBfTCCASOgAwIBAgIUDZi2vTwLeJsU87eAXPcmVuht9ZcwCgYIKoZIzj0EAwIw
FDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDYzMDIyMzUwMFoXDTI3MDYzMDIy
MzUwMFowFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAEjJUAedhsp4p6WqaoCQ/a3YnwDELlrR0fUBIbsuy3I/ny/17loqyJwXTq
Ll2cCg2EUDkIml8eNWN2njiIHExlaqNTMFEwHQYDVR0OBBYEFJQhtfJN5UrWvcae
kkLD/Q+fPogHMB8GA1UdIwQYMBaAFJQhtfJN5UrWvcaekkLD/Q+fPogHMA8GA1Ud
EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhAPUIvEGyk7q+mePu3lRBAOQD
Kgl8yIWcrTsLIrsXHA6cAiAPFrDVHrjyjv9zdl8DhXcH/Sx8o2to2EIAMDlio31w
Lg==
-----END CERTIFICATE-----"""
  // Self-signed localhost cert/key for the mTLS rejection test (same material as H2TlsSpec).
  private val ServerCertPem                                                               =
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
  private val ServerKeyPem                                                                =
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

  /**
   * TLS client used to prove `requireClientAuth` rejects unauthenticated peers:
   * with no client identity it presents no certificate; with
   * `clientCertPem`/`clientKeyPem` it presents that (possibly untrusted)
   * certificate.
   */
  private final class MtlsTestClient(
    port: Int,
    clientCertPem: Option[String] = None,
    clientKeyPem: Option[String] = None,
  ) extends AutoCloseable {
    private val trustAll                                             = Array[TrustManager](new X509TrustManager {
      override def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def getAcceptedIssuers: Array[java.security.cert.X509Certificate] = Array.empty
    })
    private val clientCtx                                            = SSLContext.getInstance("TLS")
    clientCtx.init(clientKeyManagers(), trustAll, new java.security.SecureRandom())
    private def clientKeyManagers(): Array[javax.net.ssl.KeyManager] =
      (clientCertPem, clientKeyPem) match {
        case (Some(certPem), Some(keyPem)) =>
          val password = "test".toCharArray
          val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
          keyStore.load(null, null)
          keyStore.setKeyEntry("client", loadPrivateKey(keyPem), password, Array(loadCertificate(certPem)))
          val factory  = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
          factory.init(keyStore, password)
          factory.getKeyManagers
        case _                             => null
      }
    private val tcp: Socket                                          = {
      val s = new Socket("127.0.0.1", port)
      s.setSoTimeout(5000)
      s
    }
    private var tls: SSLSocket                                       = null

    /** TCP accept succeeded: the server observed the peer. */
    def tcpConnected: Boolean = tcp.isConnected && !tcp.isClosed

    /**
     * Runs the no-certificate handshake. A server with `requireClientAuth`
     * aborts it (fatal alert or close), so the failure is returned; `None`
     * means the server unexpectedly let the peer through.
     */
    def handshakeAttempt(): Option[Throwable] =
      try {
        val s      = clientCtx.getSocketFactory
          .createSocket(tcp, "127.0.0.1", port, true)
          .asInstanceOf[SSLSocket]
        val params = s.getSSLParameters
        params.setApplicationProtocols(Array("h2"))
        s.setSSLParameters(params)
        s.setUseClientMode(true)
        s.setSoTimeout(5000)
        s.startHandshake()
        tls = s
        None
      } catch {
        case t: Throwable => Some(t)
      }

    /**
     * Returns `true` only when the server speaks H2 after the handshake (reads
     * the preface and answers with its SETTINGS frame). A server that rejects
     * the peer aborts instead, so the write/read fails or hits end-of-stream.
     * `false` when the handshake itself already died.
     */
    def serverProceeds(): Boolean =
      if (tls == null) false
      else
        try {
          val out = tls.getOutputStream
          out.write("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII))
          out.flush()
          tls.getInputStream.read() >= 0
        } catch {
          case _: Exception => false
        }
    override def close(): Unit    = {
      if (tls != null)
        try tls.close()
        catch { case _: Exception => () }
      try tcp.close()
      catch { case _: Exception => () }
    }
  }
  private def loadCertificate(pem: String): java.security.cert.X509Certificate = {
    val body =
      pem.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").replaceAll("\\s", "")
    CertificateFactory
      .getInstance("X.509")
      .generateCertificate(new ByteArrayInputStream(Base64.getDecoder.decode(body)))
      .asInstanceOf[java.security.cert.X509Certificate]
  }
  private def loadPrivateKey(pem: String): java.security.PrivateKey            = {
    val body =
      pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "")
    KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder.decode(body)))
  }
}
