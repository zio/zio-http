package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.nio.charset.StandardCharsets

import javax.net.ssl.{SSLContext, SSLSocket, TrustManager, X509TrustManager}

import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.streams.Stream
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack, HpackDecoder, HpackEncoder}
import zio.http.ResultType._
import zio.http.{
  AlpnPolicy,
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  Handler,
  Http2Config,
  LoomServer,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  Status,
  TlsConfig,
  TlsSource,
  handler,
}

/**
 * Todo 16: protocol-security H2 integration suite over a REAL [[LoomServer]]
 * plus raw-wire and JDK clients (never TestClient-only).
 *
 * Adversarial wire lens (security-review): every test crafts the attacker's
 * input explicitly and asserts WIRE observables (frame bytes/codes, handshake
 * failures, session protocols) — not state flags:
 *   1. maxConcurrentStreams is honored on the wire (burst refused, never
 *      silently exceeded);
 *   2. oversized header block carries RST_STREAM(ENHANCE_YOUR_CALM) or
 *      GOAWAY(PROTOCOL_ERROR) with no header leak into the handler;
 *   3. ALPN strict rejects an h1.1-only client at the TLS layer while
 *      NegotiateH2Preferred selects h2 for an h2 client (real handshakes);
 *   4. the TLS version pin is enforced on the wire (old-TLS client rejected,
 *      pinned version negotiated);
 *   5. idle timeout emits GOAWAY(NO_ERROR) with a valid lastStreamId (never
 *      Int.MaxValue) and drains to TCP close;
 *   6. an unknown-length streaming response arrives chunked-correct across DATA
 *      frames under flow control (byte-exact, no truncation).
 *
 * Characterization-with-teeth: T1–T15 already landed this behavior, so each
 * test additionally discriminates — the oversized-header test replays the
 * identical attacker probe against a weakened (large-limit) config and requires
 * 200 there, proving the rejection comes from the bound.
 *
 * Every transfer larger than 64KB tops up connection/stream windows per RFC
 * 9113 section 6.9 (see [[RawH2Client.topUp]]); without it the server's
 * FlowController parks forever and the test — not the server — is at fault.
 */
object H2HardeningIntegrationSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2HardeningIntegrationSpec")(
      test("attacker burst past maxConcurrentStreams is refused on the wire, never silently exceeded") {
        val perStream = 16 * 1024
        val routes    = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(perStream, 1024, 70L))),
              )
            },
          ),
        )
        withLoom(
          routes,
          Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C(Http2Config(maxConcurrentStreams = 2))),
        ) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              // Attacker occupies both mux slots; response HEADERS prove handlers run.
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 3, endStream = true))
              var heads = Set.empty[Int]
              client.socket.setSoTimeout(20000)
              while (heads.size < 2)
                client.readFrame() match {
                  case Headers(sid, _, false, _, _, _) if sid == 1 || sid == 3 => heads += sid
                  case _: WindowUpdate | _: Settings | _: Ping                 => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(heads == Set(1, 3))

              // Attacker burst 2x past the limit: both refused, never queued/served.
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 5, endStream = true))
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 7, endStream = true))
              var refused       = Set.empty[Int]
              val burstDeadline = java.lang.System.currentTimeMillis() + 10000L
              while (refused.size < 2 && java.lang.System.currentTimeMillis() < burstDeadline)
                client.readFrame() match {
                  case RstStream(sid, code) if sid == 5 || sid == 7        =>
                    println(s"[H2HardeningIntegrationSpec] mux-proof refused stream=$sid code=$code")
                    assertTrue(code == H2Error.Code.REFUSED_STREAM)
                    refused += sid
                  case Headers(sid, _, _, _, _, _) if sid == 5 || sid == 7 =>
                    throw new AssertionError("Over-limit stream was served instead of refused (mux leak)")
                  case Data(sid, _, _, _) if sid == 5 || sid == 7          =>
                    throw new AssertionError("Over-limit stream produced DATA (mux leak)")
                  case _: Data | _: Headers | _: WindowUpdate              => ()
                  case _: Settings | _: Ping | _: GoAway                   => ()
                  case _: RstStream | _: Continuation | _: Priority        => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(refused == Set(5, 7))

              // Under-limit streams still finish byte-exact (16KB fits the 64KB windows).
              var totals  = Map.empty[Int, Long].withDefaultValue(0L)
              var ended   = Set.empty[Int]
              val finDone = java.lang.System.currentTimeMillis() + 20000L
              while (ended.size < 2 && java.lang.System.currentTimeMillis() < finDone)
                client.readFrame() match {
                  case Data(sid, data, end, _) if sid == 1 || sid == 3 =>
                    totals += (sid -> (totals(sid) + data.length))
                    if (end) ended += sid
                  case _: Headers | _: WindowUpdate | _: Settings      => ()
                  case _: Ping | _: RstStream | _: Continuation        => ()
                  case _: Priority | _: GoAway                         => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              println(s"[H2HardeningIntegrationSpec] mux-proof totals=$totals ended=$ended")
              assertTrue(
                ended == Set(1, 3),
                totals.getOrElse(1, -1L) == perStream.toLong,
                totals.getOrElse(3, -1L) == perStream.toLong,
              )

              // Connection survived the attack: a fresh stream round-trips.
              val next = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 9)
              assertTrue(next.status == 200)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("attacker oversized header block draws RST/GOAWAY with correct codes, never leaks to handler") {
        val bigValue = List.fill(11264)("a").mkString
        val routes   = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        val strict   = Connector(
          bind = BindAddress.localhost(0),
          protocol = Protocol.H2C(Http2Config(maxHeaderListSize = 1024)),
        )
        withLoom(routes, strict) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              // Attacker: header list ~11KB against a 1KB bound.
              val block                                    = Hpack.encode(
                List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                  HeaderField("x-big", bigValue),
                ),
              )
              client.sendFrame(Headers(streamId = 1, headerBlock = block, endStream = true, endHeaders = true))
              var done: Either[H2Error.Code, H2Error.Code] = null
              val deadline                                 = java.lang.System.currentTimeMillis() + 10000L
              client.socket.setSoTimeout(10000)
              while (done == null && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case Settings(false, _)                      => client.sendFrame(Settings(ack = true, Nil))
                  case Settings(true, _)                       => ()
                  case _: WindowUpdate                         => ()
                  case RstStream(sid, code) if sid == 1        => done = Right(code)
                  case GoAway(_, code, _)                      => done = Left(code)
                  case Headers(sid, _, _, _, _, _) if sid == 1 =>
                    throw new AssertionError("Oversized headers were served instead of rejected (header leak)")
                  case Data(sid, _, _, _) if sid == 1          =>
                    throw new AssertionError("Oversized headers produced a body (header leak)")
                  case _                                       => ()
                }
              println(s"[H2HardeningIntegrationSpec] header-proof outcome=$done")
              assertTrue(
                done != null,
                done == Right(H2Error.Code.ENHANCE_YOUR_CALM) || done == Left(H2Error.Code.PROTOCOL_ERROR),
              )
            } finally client.close()
          }
        }.flatMap { rejectResult =>
          // Discrimination control: the IDENTICAL attacker probe against a
          // weakened (64KB-limit) config must succeed with 200 — proving the
          // rejection above came from the bound, not a blanket failure.
          val weakened = Connector(
            bind = BindAddress.localhost(0),
            protocol = Protocol.H2C(Http2Config(maxHeaderListSize = 65536)),
          )
          withLoom(routes, weakened) { port =>
            ZIO.attemptBlocking {
              val client = new RawH2Client(port, autoWindowUpdate = false)
              try {
                val block  = Hpack.encode(
                  List(
                    HeaderField(":method", "GET"),
                    HeaderField(":path", "/"),
                    HeaderField(":scheme", "http"),
                    HeaderField(":authority", s"127.0.0.1:$port"),
                    HeaderField("x-big", bigValue),
                  ),
                )
                client.sendFrame(Headers(streamId = 1, headerBlock = block, endStream = true, endHeaders = true))
                val status = client.awaitStatus(streamId = 1)
                println(s"[H2HardeningIntegrationSpec] header-proof weakened-status=$status")
                assertTrue(status == 200)
              } finally client.close()
            }
          }.map(weakResult => rejectResult && weakResult)
        }
      },
      test("ALPN strict rejects h1.1-only on the wire, negotiate selects h2 (real handshakes)") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("alpn-harness-ok"))))
        val strict = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
        )
        withLoom(routes, Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(strict))) { strictPort =>
          ZIO
            .attemptBlocking(handshakeOnly(strictPort, Array("http/1.1")))
            .exit
            .map { exit =>
              // Wire proof: the TLS layer itself rejects the h1.1-only attacker.
              val rejectedAtTls = exit match {
                case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
                case _                   => false
              }
              println(s"[H2HardeningIntegrationSpec] alpn-proof strict-rejected=$rejectedAtTls")
              assertTrue(exit.isFailure, rejectedAtTls)
            }
            .flatMap { strictResult =>
              val negotiate = TlsConfig(
                certChain = TlsSource.PemString(Secret(TestCert)),
                privateKey = TlsSource.PemString(Secret(TestKey)),
                alpnProtocols = List("h2", "http/1.1"),
                alpnPolicy = AlpnPolicy.NegotiateH2Preferred,
              )
              withLoom(routes, Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(negotiate))) { port =>
                ZIO.attemptBlocking {
                  // Wire proof the ALPN list came from config: h2 negotiated…
                  val negotiated           = handshakeOnly(port, Array("h2"))
                  // …an h2 GET round-trips over the JDK client with HTTP_2…
                  val (version, status, _) = jdkGet(port, "/")
                  // …and an http/1.1-only client now completes TLS.
                  val fallback             = handshakeOnly(port, Array("http/1.1"))
                  println(
                    s"[H2HardeningIntegrationSpec] alpn-proof negotiated=$negotiated version=$version status=$status fallback=$fallback",
                  )
                  assertTrue(
                    negotiated == "h2",
                    version == java.net.http.HttpClient.Version.HTTP_2,
                    status == 200,
                    fallback == "http/1.1",
                  )
                }
              }.map(negotiateResult => strictResult && negotiateResult)
            }
        }
      },
      test("TLS pin enforced on the wire: old-TLS attacker rejected, pinned version negotiated") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("tls-pin-harness-ok"))))
        val pinned = TlsConfig(
          certChain = TlsSource.PemString(Secret(TestCert)),
          privateKey = TlsSource.PemString(Secret(TestKey)),
          tlsVersions = List("TLSv1.3"),
        )
        withLoom(routes, Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(pinned))) { port =>
          ZIO.attemptBlocking {
            // Wire proof: the negotiated session protocol comes from the pin, ALPN still h2.
            val (sessionProtocol, negotiated) =
              handshakeWithProtocol(port, Array("h2"), Array("TLSv1.3", "TLSv1.2"))
            println(
              s"[H2HardeningIntegrationSpec] tls-proof session=$sessionProtocol negotiated=$negotiated",
            )
            assertTrue(sessionProtocol == "TLSv1.3", negotiated == "h2")
          }.flatMap { pinResult =>
            // Attacker: TLSv1.2-only client against a TLSv1.3-pinned server.
            ZIO.attemptBlocking(handshakeWithProtocol(port, Array("h2"), Array("TLSv1.2"))).exit.map { exit =>
              val rejectedAtTls = exit match {
                case Exit.Failure(cause) => cause.failures.exists(_.isInstanceOf[javax.net.ssl.SSLException])
                case _                   => false
              }
              println(s"[H2HardeningIntegrationSpec] tls-proof old-tls-rejected=$rejectedAtTls")
              assertTrue(exit.isFailure, rejectedAtTls) && pinResult
            }
          }
        }
      },
      test("idle timeout emits GOAWAY with valid lastStreamId, then drains to TCP close") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        val idle   = Connector(bind = BindAddress.localhost(0), idleTimeout = java.time.Duration.ofMillis(300))
        withLoom(routes, idle) { port =>
          ZIO.attemptBlocking {
            val client    = new RawH2Client(port, autoWindowUpdate = false)
            var wireBytes = Chunk.empty[Byte]
            try {
              // One real stream first, so lastStreamId must be exactly 1.
              val first  = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              assertTrue(first.status == 200)
              val goAway = client.awaitGoAway(10000, bytesSeen => wireBytes = wireBytes ++ bytesSeen)
              // Wire proof: GOAWAY bytes observed, valid lastStreamId (never Int.MaxValue).
              val valid  =
                goAway != null &&
                  goAway.errorCode == H2Error.Code.NO_ERROR &&
                  goAway.lastStreamId == 1 &&
                  goAway.lastStreamId != Int.MaxValue
              val onWire = wireBytes.nonEmpty
              println(
                s"[H2HardeningIntegrationSpec] idle-proof code=${if (goAway == null) "none" else goAway.errorCode} " +
                  s"lastStream=${if (goAway == null) "none" else goAway.lastStreamId} onWire=$onWire",
              )
              assertTrue(valid, onWire)
              // Drain proof: after the drain period the server closes TCP.
              client.socket.setSoTimeout(10000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: java.io.IOException => true }
              assertTrue(closed)
            } finally client.close()
          }
        }
      },
      test("unknown-length streaming response arrives chunked-correct under flow control, byte-exact") {
        // Attacker-shaped read: 200KB (> 3x the 64KB windows) with an
        // unknown length — any materialization, truncation, or stall fails.
        val total  = 200 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withLoom(routes, Connector(bind = BindAddress.localhost(0))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = true)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              val body     = mutable.ArrayBuffer.empty[Byte]
              var frames   = 0
              var done     = false
              var sawHead  = false
              client.socket.setSoTimeout(20000)
              val deadline = java.lang.System.currentTimeMillis() + 30000L
              while (!done && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case Settings(false, _)          => client.sendFrame(Settings(ack = true, Nil))
                  case Settings(true, _)           => ()
                  case _: WindowUpdate             => ()
                  case _: Ping                     => ()
                  case Headers(1, _, end, _, _, _) =>
                    sawHead = true
                    done = end
                  case Data(1, data, end, _)       =>
                    frames += 1
                    body ++= data.toArray[Byte].toSeq
                    // RFC 9113 6.9: replenish windows as DATA is consumed,
                    // or the 200KB transfer stalls the server's FlowController.
                    client.topUp(1, data.length)
                    done = end
                  case RstStream(1, code)          =>
                    throw new AssertionError("Stream reset mid-transfer: " + code)
                  case GoAway(_, code, _)          =>
                    throw new AssertionError("GOAWAY mid-transfer: " + code)
                  case _: Data | _: Headers | _: RstStream | _: Continuation | _: Priority | _: GoAway => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              // Expected bytes regenerated deterministically (same (i % 251) source).
              var exact    = body.length == total
              var i        = 0
              while (exact && i < total) {
                if (body(i) != ((i % 251) & 0xff).toByte) exact = false
                i += 1
              }
              println(
                s"[H2HardeningIntegrationSpec] stream-proof frames=$frames bytes=${body.length} endSeen=$done exact=$exact",
              )
              assertTrue(
                sawHead,
                done,
                // Chunked across DATA frames (16KB maxFrameSize forces many), never one blob.
                frames > 1,
                body.length == total,
                exact,
              )
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
    ) @@ sequential

  // ─── harness ──────────────────────────────────────────────────────────────

  /**
   * Lazily-generated deterministic bytes: O(1) source memory, unknown length.
   */
  private def unfoldingBytes(
    total: Int,
    throttleEvery: Int = Int.MaxValue,
    throttleMs: Long = 0L,
  ): Stream[Nothing, Byte] =
    Stream.unfold(0) { i =>
      if (i >= total) None
      else {
        if (throttleMs > 0L && (i % throttleEvery == 0) && i > 0) Thread.sleep(throttleMs)
        Some((((i % 251) & 0xff).toByte, i + 1))
      }
    }

  private def withLoom[R](
    routes: Routes[Any],
    connector: Connector,
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(LoomServer(connector).serve(routes, Context.empty)),
      )(handle => ZIO.succeed(handle.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding: " + other)
        }
        use(port)
      }

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

  /**
   * Raw TLS handshake returning the negotiated TLS session protocol and the
   * negotiated ALPN protocol. Throws when negotiation fails.
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

  /**
   * JDK HTTP/2 client GET over TLS: returns (session version, status, body).
   */
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

  private final class RawH2Client(val port: Int, autoWindowUpdate: Boolean = true) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket          = new Socket("127.0.0.1", port)
    socket.setSoTimeout(20000)
    private val out     = socket.getOutputStream
    private val rawIn   = socket.getInputStream
    private var buf     = Chunk.empty[Byte]
    private val encoder = new HpackEncoder()
    private val decoder = new HpackDecoder()

    handshake()

    def sendFrame(frame: H2Frame): Unit   = sendRaw(FrameCodec.encode(frame).toArray)
    def sendRaw(bytes: Array[Byte]): Unit = { out.write(bytes); out.flush() }

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buf) match {
          case Right((frame, rest))           =>
            buf = rest; return frame
          case Left(H2Error.InsufficientData) =>
            val tmp = new Array[Byte](8192)
            val n   = rawIn.read(tmp)
            if (n < 0) throw new java.io.EOFException("Connection closed")
            buf = buf ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, n))
          case Left(err)                      =>
            throw new AssertionError("Frame decode error: " + err)
        }
      }
      throw new AssertionError("unreachable")
    }

    def makeHeaders(method: String, path: String, streamId: Int, endStream: Boolean): Headers =
      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(
          List(
            HeaderField(":method", method),
            HeaderField(":path", path),
            HeaderField(":scheme", "http"),
            HeaderField(":authority", s"127.0.0.1:$port"),
          ),
        ),
        endStream = endStream,
        endHeaders = true,
      )

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse = {
      sendFrame(makeHeaders(method, path, streamId, endStream = body.isEmpty))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def awaitResponse(streamId: Int): RawResponse = {
      val hdrs   = mutable.ListBuffer.empty[HeaderField]
      var body   = Chunk.empty[Byte]
      var done   = false
      while (!done) {
        readFrame() match {
          case Settings(false, _)                                   => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                    => ()
          case _: WindowUpdate                                      => ()
          case _: Ping                                              => ()
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            decoder.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            done = end
          case Data(sid, data, end, _) if sid == streamId           =>
            body = body ++ data; done = end
            noteReceived(sid, data.length)
          case GoAway(_, code, dbg)                                 =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _: RstStream | _: Continuation | _: Priority         => ()
          case other if other.streamId == streamId                  =>
            throw new AssertionError("Unexpected frame for stream: " + other)
          case _                                                    => ()
        }
      }
      val status = hdrs
        .find(_.name == ":status")
        .map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      RawResponse(status, hdrs.toList, body)
    }

    def awaitStatus(streamId: Int): Int = {
      var status: Option[Int] = None
      while (status.isEmpty) {
        readFrame() match {
          case Settings(false, _)                                   => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                    => ()
          case _: WindowUpdate                                      => ()
          case RstStream(sid, code) if sid == streamId              =>
            throw new AssertionError("Stream was reset unexpectedly: " + code)
          case GoAway(_, code, _)                                   =>
            throw new AssertionError("GOAWAY while awaiting response: " + code)
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            decoder.decode(block) match {
              case Right(fields) =>
                status = fields.find(_.name == ":status").map(_.value.toInt)
                if (end && status.isEmpty) throw new AssertionError("Missing :status")
              case Left(error)   => throw new AssertionError("HPACK decode: " + error)
            }
          case _                                                    => ()
        }
      }
      status.get
    }

    /**
     * Reads until a GOAWAY arrives or `timeoutMs` elapses; reports raw bytes
     * seen.
     */
    def awaitGoAway(timeoutMs: Long, onBytes: Chunk[Byte] => Unit): GoAway = {
      val deadline       = java.lang.System.currentTimeMillis() + timeoutMs
      var result: GoAway = null
      while (result == null && java.lang.System.currentTimeMillis() < deadline) {
        socket.setSoTimeout(Math.max(1, (deadline - java.lang.System.currentTimeMillis()).toInt))
        try {
          readFrame() match {
            case s: Settings     => if (!s.ack) sendFrame(Settings(ack = true, Nil))
            case _: WindowUpdate => ()
            case g: GoAway       =>
              onBytes(FrameCodec.encode(g))
              result = g
            case _               => ()
          }
        } catch {
          case _: java.net.SocketTimeoutException => ()
          case _: EOFException                    => return result
        }
      }
      if (result == null) onBytes(Chunk.empty[Byte])
      result
    }

    override def close(): Unit = socket.close()

    /**
     * A real HTTP/2 receiver replenishes the sender's windows as it consumes
     * DATA (RFC 9113 6.9): without this, any transfer larger than the 64KB
     * default connection window stalls the server's FlowController forever.
     */
    private def noteReceived(streamId: Int, bytes: Int): Unit =
      if (autoWindowUpdate && bytes > 0) topUp(streamId, bytes)

    /** Replenish the sender's connection- and stream-level windows. */
    def topUp(streamId: Int, bytes: Int): Unit =
      if (bytes > 0) {
        sendFrame(WindowUpdate(streamId = 0, increment = bytes))
        sendFrame(WindowUpdate(streamId = streamId, increment = bytes))
      }

    private def handshake(): Unit = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      readFrame() match {
        case Settings(false, _) => sendFrame(Settings(ack = true, Nil))
        case other              => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame() // consume server's ACK for our SETTINGS
    }
  }

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte])
}
