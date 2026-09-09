package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.ResultType._
import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, HpackDecoder, HpackEncoder}
import zio.http.{
  AccessLogRecord,
  AccessLogSink,
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  Handler,
  Http2Config,
  LoomServer,
  Method,
  Middleware,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  Status,
  TrustedProxyConfig,
  handler,
}

/**
 * Integration gate for the H2 hardening waves.
 *
 * Runs the adversarial cells — oversize headers (G1), oversize body (G1), slow
 * loris (G2), spoofed forwarding headers (G3), and the HMAC raw-body tap plus
 * the access-log sink (G4) — against ONE hardened `LoomServer` loopback with
 * all knobs on, plus a knobs-off cell proving the timeouts are what reset slow
 * streams. Each cell isolates its attack per-stream: the reset carries the
 * expected `RST_STREAM` code and a sibling stream on the same connection still
 * gets a 200, except for the HPACK-poison cell where the attack corrupts
 * connection-level compression state and the server correctly tears the whole
 * connection down instead (nothing served).
 *
 * Wire patterns are the same attacks pinned by the wave specs
 * (`MaxHeaderListSizeSpec`, `H2BodyBoundSpec`, `H2SlowStreamSpec`,
 * `H2ProxyTrustSpec`, `H2AccessLogSpec`, `H2RawBodySpec`); this spec combines
 * them, it does not redefine enforcement semantics. If a cell fails, the bug is
 * reported, not fixed here.
 */
@experimental
object H2HardeningMatrixSpec extends ZIOSpecDefault {

  private val BodyCap: Long         = 1024L
  private val HeaderTimeoutMs: Long = 1000L
  private val BodyTimeoutMs: Long   = 2000L

  /**
   * RFC 4231 Test Case 1 key: 20 bytes of 0x0b (same vector as H2RawBodySpec).
   */
  private val TestKey: Array[Byte] =
    Array.fill(20)(0x0b.toByte)

  /** RFC 4231 Test Case 1 message: "Hi There". */
  private val TestMessage: Chunk[Byte] =
    Chunk.fromArray("Hi There".getBytes(StandardCharsets.US_ASCII))

  /** RFC 4231 Test Case 1 HMAC-SHA256, lowercase hex. */
  private val ExpectedMacHex: String =
    "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"

  /**
   * Raw-byte HMAC-SHA256 tap: hash `bytes` exactly as received, no decoding.
   */
  private def hmacSha256Hex(key: Array[Byte], bytes: Chunk[Byte]): String = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(new SecretKeySpec(key, "HmacSHA256"))
    val out = new StringBuilder(64)
    val sum = mac.doFinal(bytes.toArray)
    var i   = 0
    while (i < sum.length) {
      out.append(Character.forDigit((sum(i) >> 4) & 0xf, 16))
      out.append(Character.forDigit(sum(i) & 0xf, 16))
      i += 1
    }
    out.toString
  }

  private val MatrixRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern.GET,
        Handler.succeed(Response.ok),
      ),
      Route(
        RoutePattern(Method.POST, "/echo"),
        handler { (req: Request) =>
          responseAsResult(Response(status = Status.Ok, body = req.body))
        },
      ),
      Route(
        RoutePattern(Method.POST, "/hmac"),
        handler { (req: Request) =>
          // v4 preserves bytes: tap the raw chunk BEFORE any decoding.
          // Verification/dedup/reconciliation belong to Qaizn, not here.
          responseAsResult(
            Response(status = Status.Ok, body = Body.fromString(hmacSha256Hex(TestKey, req.body.toChunk))),
          )
        },
      ),
      Route(
        RoutePattern(Method.GET, "/whoami"),
        handler { (req: Request) =>
          val clientIp        = req.headers.rawGet("x-client-ip").getOrElse("none")
          val peer            = req.headers.rawGet("x-peer-address").getOrElse("none")
          val forwardedFor    = req.headers.rawGet("x-forwarded-for").getOrElse("none")
          val forwardedHeader = req.headers.rawGet("forwarded").getOrElse("none")
          responseAsResult(
            Response(
              status = Status.Ok,
              body = Body.fromString(s"$clientIp|$peer|$forwardedFor|$forwardedHeader"),
            ),
          )
        },
      ),
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2HardeningMatrixSpec")(
      test("matrix: oversize headers reset with ENHANCE_YOUR_CALM and the connection survives") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              client.sendOversizedHeaders(streamId = 1, bigValue = List.fill(11264)("a").mkString)
              val reset   = client.awaitReset(streamId = 1, timeoutMs = 8000)
              val sibling = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(
                reset == H2Error.Code.ENHANCE_YOUR_CALM,
                sibling.status == 200,
              )
            } finally client.close()
          }
        }
      },
      test("matrix: oversize body resets with FLOW_CONTROL_ERROR and the connection survives") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              // Split across two DATA frames so the running total crosses the
              // cap mid-stream, exactly like H2BodyBoundSpec.
              val first   = Chunk.fromArray(Array.fill(BodyCap.toInt)(0x41.toByte))
              val second  = Chunk.fromArray(Array(0x42.toByte))
              val code    = client.postSplitOverCap(streamId = 1, first = first, second = second)
              val sibling = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(code == H2Error.Code.FLOW_CONTROL_ERROR, sibling.status == 200)
            } finally client.close()
          }
        }
      },
      test("matrix: stalled headers (slow loris) reset with CANCEL and the connection survives") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              // Empty first fragment with END_HEADERS=false, CONTINUATION never
              // arrives — the shared HPACK table stays in sync (H2SlowStreamSpec).
              client.sendSplitHeaders(streamId = 1)
              val code    = client.awaitReset(streamId = 1, timeoutMs = 8000)
              val sibling = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(code == H2Error.Code.CANCEL, sibling.status == 200)
            } finally client.close()
          }
        }
      },
      test("matrix: spoofed forwarding header under default-deny has zero effect") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              val extra = List(
                HeaderField("x-forwarded-for", "203.0.113.7"),
                HeaderField("x-forwarded-proto", "https"),
                HeaderField("forwarded", "for=203.0.113.7;proto=https"),
              )
              val seen  = client.getWhoami(streamId = 1, extra = extra)
              assertTrue(
                seen.clientIp == "127.0.0.1",
                seen.peer == "127.0.0.1",
                seen.forwardedFor == "none",
                seen.forwardedHeader == "none",
              )
            } finally client.close()
          }
        }
      },
      test("matrix: HMAC body over the hardened connection matches the RFC 4231 vector") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              val mac  = client.post("/hmac", TestMessage, streamId = 1)
              val echo = client.post("/echo", TestMessage, streamId = 3)
              assertTrue(
                mac.status == 200,
                mac.bodyText == ExpectedMacHex,
                echo.status == 200,
                echo.body == TestMessage,
              )
            } finally client.close()
          }
        }
      },
      test("matrix knobs-off: slow drip completes when header/body timeouts are disabled") {
        withKnobsOffServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              // 7 bytes x 500ms gaps = ~3s total: over the 2s hardened
              // bodyTimeout, but the knobs-off server disables both timeouts.
              val response = client.postDripExpectResponse(streamId = 1, totalBytes = 7, gapMs = 500L)
              assertTrue(response.status == 200, response.body.length == 7)
            } finally client.close()
          }
        }
      },
      test("matrix: sink holds metadata for served requests and no body bytes") {
        val secret = "matrix-secret-body-payload-9d2b4f"
        withMatrixServer { (port, sink) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              val get           = client.getWithId(path = "/", streamId = 1, requestId = "matrix-req-1")
              val post          =
                client.post("/echo", Chunk.fromArray(secret.getBytes(StandardCharsets.UTF_8)), streamId = 3)
              val records       = sink.records
              val recordStrings = records.map(_.toString)
              assertTrue(
                get.status == 200,
                post.status == 200,
                records.length == 2,
                records.exists(r => r.method == "GET" && r.requestId == "matrix-req-1" && r.status == 200),
                records.exists(r => r.method == "POST" && r.status == 200),
                records.forall(r => r.requestId.nonEmpty && r.trustDecision.nonEmpty && r.deadlineOutcome.nonEmpty),
                !recordStrings.exists(_.contains(secret)),
              )
            } finally client.close()
          }
        }
      },
      test("matrix: HPACK contract violation tears the connection down without serving") {
        withMatrixServer { (port, _) =>
          ZIO.attemptBlocking {
            val client = new MatrixClient(port)
            try {
              // An undecodable header block corrupts connection-level HPACK
              // state: the reader loop fails the connection (TCP close, no
              // per-stream RST possible) and nothing is ever served. Current
              // behavior sends no GOAWAY before the FIN (reader-loop
              // `case _: IOException => ()`); this cell pins the teardown, not
              // the frame choice — if a GOAWAY appears, update the cell.
              val poison = H2Frame.Headers(
                streamId = 1,
                headerBlock = Chunk.fromArray(Array(0x80.toByte)),
                endStream = true,
                endHeaders = true,
              )
              client.sendRaw(FrameCodec.encode(poison).toArray)
              val closed = client.awaitClosed()
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
    ) @@ sequential

  private def hardenedConnector: Connector =
    Connector(
      bind = BindAddress.localhost(0),
      protocol = Protocol.H2C(Http2Config(maxHeaderListSize = 1024)),
      maxRequestBodySize = BodyCap,
      headerTimeoutMs = HeaderTimeoutMs,
      bodyTimeoutMs = BodyTimeoutMs,
      trustedProxy = TrustedProxyConfig.default,
    )

  private def withMatrixServer[R](
    use: (Int, CaptureSink) => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    withServer(hardenedConnector, use)

  private def withKnobsOffServer[R](
    use: (Int, CaptureSink) => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    withServer(hardenedConnector.copy(headerTimeoutMs = 0L, bodyTimeoutMs = 0L), use)

  private def withServer[R](
    connector: Connector,
    use: (Int, CaptureSink) => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val sink   = CaptureSink()
          val handle =
            new LoomServer(connector).serve(MatrixRoutes @@ Middleware.accessLog(sink), Context.empty)
          (handle, sink)
        },
      ) { case (handle, _) => ZIO.attemptBlocking(handle.shutdownAndWait()).ignore }
      .flatMap { case (handle, sink) =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port, sink)
      }

  private final class CaptureSink extends AccessLogSink {
    private val queue                      = new java.util.concurrent.ConcurrentLinkedQueue[AccessLogRecord]()
    def log(record: AccessLogRecord): Unit = { queue.add(record); () }
    def records: List[AccessLogRecord]     = queue.toArray(new Array[AccessLogRecord](0)).toList
  }

  private object CaptureSink {
    def apply(): CaptureSink = new CaptureSink()
  }

  private final case class WhoSeen(clientIp: String, peer: String, forwardedFor: String, forwardedHeader: String)

  private final case class MatrixResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte]) {
    def bodyText: String = new String(body.toArray, StandardCharsets.UTF_8)
  }

  private final class MatrixClient(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(20000)
    private val out   = socket.getOutputStream
    private val rawIn = socket.getInputStream
    private var buf   = Chunk.empty[Byte]

    private val encoder = new HpackEncoder()
    private val decoder = new HpackDecoder()

    handshake()

    def sendFrame(frame: H2Frame): Unit   = sendRaw(FrameCodec.encode(frame).toArray)
    def sendRaw(bytes: Array[Byte]): Unit = { out.write(bytes); out.flush() }

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): MatrixResponse = {
      sendFrame(makeHeaders(method, path, streamId, endStream = body.isEmpty, Nil))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def getWithId(path: String, streamId: Int, requestId: String): MatrixResponse = {
      sendFrame(makeHeaders("GET", path, streamId, endStream = true, List(HeaderField("x-request-id", requestId))))
      awaitResponse(streamId)
    }

    def post(path: String, body: Chunk[Byte], streamId: Int): MatrixResponse = {
      sendFrame(makeHeaders("POST", path, streamId, endStream = body.isEmpty, Nil))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def getWhoami(streamId: Int, extra: List[HeaderField]): WhoSeen = {
      sendFrame(makeHeaders("GET", "/whoami", streamId, endStream = true, extra))
      val response = awaitResponse(streamId)
      if (response.status != 200) throw new AssertionError("whoami: " + response.status)
      response.bodyText.split("\\|", -1).toList match {
        case List(clientIp, peer, forwardedFor, forwardedHeader) =>
          WhoSeen(clientIp, peer, forwardedFor, forwardedHeader)
        case other                                               =>
          throw new AssertionError("whoami shape: " + other)
      }
    }

    def sendOversizedHeaders(streamId: Int, bigValue: String): Unit = {
      val pseudo = List(
        HeaderField(":method", "GET"),
        HeaderField(":path", "/"),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
        HeaderField("x-big", bigValue),
      )
      sendFrame(
        H2Frame.Headers(
          streamId = streamId,
          headerBlock = encoder.encode(pseudo),
          endStream = true,
          endHeaders = true,
        ),
      )
    }

    def sendSplitHeaders(streamId: Int): Unit =
      sendFrame(H2Frame.Headers(streamId, Chunk.empty, endStream = true, endHeaders = false))

    def postSplitOverCap(streamId: Int, first: Chunk[Byte], second: Chunk[Byte]): H2Error.Code = {
      sendFrame(makeHeaders("POST", "/echo", streamId, endStream = false, Nil))
      sendFrame(Data(streamId, first, endStream = false))
      sendFrame(Data(streamId, second, endStream = true))
      awaitReset(streamId, timeoutMs = 8000)
    }

    def postDripExpectResponse(streamId: Int, totalBytes: Int, gapMs: Long): MatrixResponse = {
      sendFrame(makeHeaders("POST", "/echo", streamId, endStream = false, Nil))
      var sent = 0
      while (sent < totalBytes) {
        val last = sent == totalBytes - 1
        sendFrame(Data(streamId, Chunk.fromArray(Array(0x41.toByte)), endStream = last))
        sent += 1
        if (!last) Thread.sleep(gapMs)
      }
      awaitResponse(streamId)
    }

    def awaitReset(streamId: Int, timeoutMs: Int): H2Error.Code = {
      socket.setSoTimeout(1000)
      try {
        val deadline                     = java.lang.System.currentTimeMillis() + timeoutMs
        var result: Option[H2Error.Code] = None
        while (result.isEmpty) {
          if (java.lang.System.currentTimeMillis() > deadline)
            throw new AssertionError("Timed out waiting for RST_STREAM on stream " + streamId)
          try {
            readFrame() match {
              case RstStream(sid, code) if sid == streamId => result = Some(code)
              case GoAway(_, code, dbg)                    =>
                throw new AssertionError(s"GOAWAY while waiting RST: $code ${new String(dbg.toArray)}")
              case _                                       => ()
            }
          } catch {
            case _: SocketTimeoutException => ()
          }
        }
        result.get
      } finally socket.setSoTimeout(20000)
    }

    /**
     * Drains until the server closes TCP: any served response (HEADERS/DATA) or
     * GOAWAY fails — the poisoned connection must go down with nothing on the
     * wire after our attack frame.
     */
    def awaitClosed(): Boolean = {
      socket.setSoTimeout(10000)
      try {
        val deadline = java.lang.System.currentTimeMillis() + 10000L
        var closed   = false
        while (!closed) {
          if (java.lang.System.currentTimeMillis() > deadline)
            throw new AssertionError("Timed out waiting for the server to close the connection")
          try {
            readFrame() match {
              case Headers(sid, _, _, _, _, _) =>
                throw new AssertionError("Response HEADERS served on poisoned stream " + sid)
              case Data(sid, _, _, _)          =>
                throw new AssertionError("Response DATA served on poisoned stream " + sid)
              case RstStream(sid, code)        =>
                throw new AssertionError(s"RST_STREAM($code) on poisoned stream $sid: expected a connection teardown")
              case _: GoAway                   =>
                throw new AssertionError("GOAWAY arrived: update the cell to pin the new frame choice")
              case _                           => ()
            }
          } catch {
            case _: SocketTimeoutException => ()
            case _: EOFException           => closed = true
          }
        }
        closed
      } finally socket.setSoTimeout(20000)
    }

    private def awaitResponse(streamId: Int): MatrixResponse = {
      val hdrs   = mutable.ListBuffer.empty[HeaderField]
      var body   = Chunk.empty[Byte]
      var done   = false
      while (!done) {
        readFrame() match {
          case Settings(false, _)                                   => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                    => ()
          case _: WindowUpdate                                      => ()
          case Ping(false, data)                                    => sendFrame(Ping(ack = true, data))
          case Ping(true, _)                                        => ()
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
      MatrixResponse(status, hdrs.toList, body)
    }

    private def makeHeaders(
      method: String,
      path: String,
      streamId: Int,
      endStream: Boolean,
      extra: List[HeaderField],
    ): H2Frame.Headers = {
      val pseudo = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      )
      H2Frame.Headers(
        streamId = streamId,
        headerBlock = encoder.encode(pseudo ++ extra),
        endStream = endStream,
        endHeaders = true,
      )
    }

    private def readFrame(): H2Frame = {
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

    override def close(): Unit = socket.close()

    private def handshake(): Unit = {
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
}
