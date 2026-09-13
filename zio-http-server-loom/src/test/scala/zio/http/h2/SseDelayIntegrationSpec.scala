package zio.http.h2

import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets

import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.streams.Stream
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, HpackDecoder, HpackEncoder}
import zio.http.ResultType._
import zio.http.sse.Sse._
import zio.http.sse.{ServerSentEvent, SseCodec}
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  Http2Config,
  Protocol,
  Request,
  Response,
  Route,
  Routes,
  ServerHandle,
  Status,
  handler,
}

/**
 * Todo 9: SSE inter-message delay preserved end-to-end over H2.
 *
 * Canonical handler shape (LoomServer style — `Routes`/`Route`/`handler` plus
 * `responseAsResult`, exactly as in `StreamingEdgeSpec`): serve
 * `Response.sse(stream)` where the event stream carries its own pacing via a
 * blocking `Thread.sleep` inside `Stream.unfold`. Loom runs each H2 stream on a
 * virtual thread, so a blocking sleep parks cheaply (no ZIO fiber, no
 * `TestClock`) — the same convention as `unfoldingBytes` throttling in
 * `StreamingEdgeSpec`. The delay must survive the full path: handler Stream →
 * `Sse.body` (per-event `flatMap`, never materialized) → `toStream` →
 * `sendStreamedBody` (`chunked(maxFrameSize)`, flow-gated) → one H2 DATA frame
 * per event → incremental client read.
 *
 * CI-slack reasoning for the 180–300ms bounds on 200ms spacing: `Thread.sleep`
 * on a Loom virtual thread wakes with ~1–5ms lateness and loopback RTT is
 * sub-millisecond, so a genuinely paced stream lands at ~200–205ms per gap; the
 * lower bound sits 20ms below nominal — far above a batched arrival (<5ms on
 * loopback, asserted <50ms by the batching-control test) — while the upper
 * bound allows 100ms of scheduling jitter under loaded CI.
 *
 * Every transfer larger than 64KB tops up connection/stream windows per RFC
 * 9113 section 6.9 (see [[RawH2Client.topUp]]); without it the server's
 * FlowController parks forever and the test — not the server — is at fault.
 */
object SseDelayIntegrationSpec extends ZIOSpecDefault {

  private val Utf8 = StandardCharsets.UTF_8

  private val EventCount = 5
  private val SpacingMs  = 200L

  /** Lower/upper bound (ms) for each inter-arrival gap on 200ms spacing. */
  private val LowerMs = 180L
  private val UpperMs = 300L

  /** Ceiling (ms) proving all-at-once arrival for the batching control. */
  private val BatchCeilingMs = 50L

  /**
   * Lazily-paced events: a sleep before EVERY event (including the first) is
   * the inter-message delay under test. Sleeping first keeps the first frame
   * from sitting in the socket buffer while the client finishes its
   * post-HEADERS transition (which would systematically shrink gap 0); all
   * timestamps below are then stamped in the hot read loop. O(1) source memory,
   * unknown length — `Sse.body` frames each event as it flows.
   */
  private def pacedEvents(total: Int, spacingMs: Long): Stream[Nothing, ServerSentEvent] =
    Stream.unfold(0) { i =>
      if (spacingMs > 0L) Thread.sleep(spacingMs)
      if (i >= total) None
      else Some((ServerSentEvent(s"e$i"), i + 1))
    }

  /**
   * Infinite paced events for disconnect tests (the server never ends these).
   */
  private def endlessEvents(spacingMs: Long): Stream[Nothing, ServerSentEvent] =
    Stream.unfold(0) { i =>
      if (spacingMs > 0L && i > 0) Thread.sleep(spacingMs)
      Some((ServerSentEvent(s"live-$i"), i + 1))
    }

  /**
   * Infinite events with a long lead time before EVERY event (including the
   * first): lets the immediate-disconnect test open the stream (response
   * HEADERS flow before any body byte) and reset it with zero DATA on the wire,
   * deterministically.
   */
  private def slowStartEvents(spacingMs: Long): Stream[Nothing, ServerSentEvent] =
    Stream.unfold(0) { i =>
      Thread.sleep(spacingMs)
      Some((ServerSentEvent(s"live-$i"), i + 1))
    }

  /** The Todo 9 handler shape: `Response.sse` over a delay-carrying stream. */
  private def sseRoutes(events: Stream[Nothing, ServerSentEvent]): Routes[Any] =
    Routes(
      Route(
        RoutePattern.GET,
        handler { (_: Request) =>
          responseAsResult(Response.sse(events))
        },
      ),
    )

  /**
   * Batching control: identical bytes, materialized into one Chunk (arrives
   * all-at-once).
   */
  private def batchingRoutes(events: List[ServerSentEvent]): Routes[Any] = {
    val all = events.foldLeft(Chunk.empty[Byte])(_ ++ SseCodec.encode(_))
    Routes(
      Route(
        RoutePattern.GET,
        handler { (_: Request) =>
          responseAsResult(Response(status = Status.Ok, body = Body.fromChunk(all)))
        },
      ),
    )
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SseDelayIntegrationSpec")(
      test("paced SSE handler preserves inter-message delay end-to-end, one DATA frame per event") {
        val events = (0 until EventCount).map(i => ServerSentEvent(s"e$i")).toList
        withRawServer(sseRoutes(pacedEvents(EventCount, SpacingMs))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // Warmup stream: settles JIT and the dispatch/framing paths so
              // cold-start stalls cannot leak into the measured gaps below.
              // The first event needs a full spacing, so this RST
              // deterministically races zero DATA.
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              val warmRst = client.awaitRst(1, 10000L)
              val warmOk  = assertTrue(warmRst.contains(H2Error.Code.CANCEL))
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 3, endStream = true, acceptSse = true))
              val headers = client.awaitHeaders(3)
              val byName  = headers.map(h => h.name.toLowerCase -> h.value).toMap
              val headOk  = assertTrue(
                byName.get(":status").contains("200"),
                byName.get("content-type").exists(_.contains("text/event-stream")),
              )
              warmOk && headOk && client.readSseData(3) { (gaps, frames, endSeen) =>
                val texts    = frames.map(b => new String(b.toArray[Byte], Utf8))
                val expected = events.map(e => new String(SseCodec.encode(e).toArray[Byte], Utf8))
                // Misleading-success guard: exact event names in order, exact
                // count (> 0), exact bytes — a vacuous or reordered pass fails.
                val namesOk  = texts.zipWithIndex.forall { case (t, i) => t == s"data: e$i\n\n" }
                val gapsOk   = gaps.length == EventCount - 1 && gaps.forall(g => g >= LowerMs && g <= UpperMs)
                assertTrue(
                  frames.length == EventCount,
                  endSeen,
                  namesOk,
                  texts == expected,
                  gapsOk,
                )
              }
            } finally client.close()
          }
        }
      },
      test("batching control: materialized Body arrives all-at-once (<50ms gaps)") {
        // RED rationale, kept green permanently: the same frame-by-frame
        // measurement that gates the paced test MUST see a batching
        // implementation arrive in a burst. If the handler ever buffered or
        // coalesced events, the main test would fail exactly like the inverse
        // of this assertion.
        val events = (0 until EventCount).map(i => ServerSentEvent(s"e$i")).toList
        withRawServer(batchingRoutes(events)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              client.readSseData(1) { (gaps, frames, endSeen) =>
                val actual   = frames.map(b => new String(b.toArray[Byte], Utf8)).mkString
                val expected = events.map(e => new String(SseCodec.encode(e).toArray[Byte], Utf8)).mkString
                // Coalescing proof: 5 events collapsed into fewer frames with
                // near-zero total span — the inverse of the paced assertion.
                val burstOk  = frames.length < EventCount && gaps.sum < BatchCeilingMs
                assertTrue(frames.nonEmpty, endSeen, burstOk, actual == expected)
              }
            } finally client.close()
          }
        }
      },
      test("client disconnect mid-stream cancels the server Stream and sends RST, no thread leak") {
        val before = countStreamThreads()
        withRawServer(sseRoutes(endlessEvents(50L))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              // Incremental read of exactly 2 events, then disconnect.
              var seen      = 0
              while (seen < 2)
                client.readFrame() match {
                  case Data(1, data, _, _) if data.nonEmpty                                            =>
                    client.topUp(1, data.length)
                    seen += 1
                  case _: WindowUpdate | _: Settings | _: Ping                                         => ()
                  case _: Headers | _: Continuation | _: Priority | _: GoAway | _: RstStream | _: Data => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              val serverRst = client.awaitRst(1, 10000L)
              println(s"[SseDelayIntegrationSpec] mid-disconnect-proof serverRst=$serverRst seenEvents=$seen")
              assertTrue(seen == 2, serverRst.contains(H2Error.Code.CANCEL))
            } finally client.close()
          }
        }.flatMap { result =>
          ZIO.attemptBlocking(awaitThreadDrain(before)).map(drained => result && assertTrue(drained))
        }
      },
      test("slow consumer: server stalls on windows instead of buffering, completes on top-up") {
        // Few large events (not many tiny ones): the ~107KB total exceeds the
        // 64KB connection window so the stall is deterministic, while fast
        // production keeps the whole stream far under the 30s request timer.
        val totalEvents  = 500
        val payload      = "v" * 200
        val events       = (0 until totalEvents).map(i => ServerSentEvent(s"slow-$i-$payload")).toList
        val expectedWire = events.map(e => new String(SseCodec.encode(e).toArray[Byte], Utf8)).mkString
        withRawServer(sseRoutes(Stream.fromIterable(events))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              val body             = mutable.ListBuffer.empty[Byte]
              // Phase 1: read with NO window top-ups until the server stalls.
              // Proves T6/T7 backpressure — no unbounded server-side buffering.
              client.socket.setSoTimeout(1500)
              var endSeen          = false
              var stalledByTimeout = false
              while (!endSeen && !stalledByTimeout)
                try
                  client.readFrame() match {
                    case Data(1, data, end, _)                                                 =>
                      body ++= data.toArray[Byte].toSeq
                      if (end) endSeen = true
                    case _: WindowUpdate | _: Settings | _: Ping                               => ()
                    case _: Headers | _: Continuation | _: Priority | _: GoAway | _: RstStream => ()
                    case other => throw new AssertionError("Unexpected frame: " + other)
                  }
                catch { case _: SocketTimeoutException => stalledByTimeout = true }
              val stalledBytes     = body.length
              println(s"[SseDelayIntegrationSpec] slow-consumer-proof stalledBytes=$stalledBytes endSeen=$endSeen")
              // 64KB connection window + at most one maxFrameSize frame in flight.
              val stallOk          = !endSeen && stalledBytes <= 65535 + 16384
              // Phase 2: consume at our pace — top up, read, repeat to END_STREAM.
              client.socket.setSoTimeout(20000)
              while (!endSeen) {
                client.topUp(1, 65535)
                client.readFrame() match {
                  case Data(1, data, end, _)                                                 =>
                    body ++= data.toArray[Byte].toSeq
                    if (data.nonEmpty) client.topUp(1, data.length)
                    if (end) endSeen = true
                  case _: WindowUpdate | _: Settings | _: Ping                               => ()
                  case _: Headers | _: Continuation | _: Priority | _: GoAway | _: RstStream => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              }
              val full             = new String(body.toArray, Utf8)
              val eventCount       = full.split("\n\n").count(_.startsWith("data: slow-"))
              println(s"[SseDelayIntegrationSpec] slow-consumer-proof events=$eventCount totalBytes=${full.length}")
              assertTrue(
                stallOk,
                stalledBytes < expectedWire.length,
                endSeen,
                eventCount == totalEvents,
                full == expectedWire,
              )
            } finally client.close()
          }
        }
      },
      test("immediate disconnect: 0 events, clean RST, no leak") {
        val before = countStreamThreads()
        // First event needs 2000ms; response HEADERS prove the stream opened
        // server-side, so the RST below deterministically races zero DATA.
        withRawServer(sseRoutes(slowStartEvents(2000L))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              var dataFrames                      = 0
              var serverRst: Option[H2Error.Code] = None
              val deadline                        = java.lang.System.currentTimeMillis() + 10000L
              client.socket.setSoTimeout(2000)
              while (serverRst.isEmpty && java.lang.System.currentTimeMillis() < deadline)
                try
                  client.readFrame() match {
                    case RstStream(1, code)                   => serverRst = Some(code)
                    case Data(1, data, _, _) if data.nonEmpty =>
                      dataFrames += 1
                      client.topUp(1, data.length)
                    case _                                    => ()
                  }
                catch { case _: SocketTimeoutException => () }
              println(
                s"[SseDelayIntegrationSpec] immediate-disconnect-proof serverRst=$serverRst dataFrames=$dataFrames",
              )
              // Writer loop and listener survive: a fresh stream round-trips.
              val probe                           = new RawH2Client(port)
              try {
                probe.sendFrame(probe.makeHeaders("GET", "/", streamId = 3, endStream = true, acceptSse = true))
                val probeHeaders = probe.awaitHeaders(3)
                val probeOk      = probeHeaders.exists(h => h.name == ":status" && h.value == "200")
                probe.sendFrame(RstStream(streamId = 3, errorCode = H2Error.Code.CANCEL))
                assertTrue(dataFrames == 0, serverRst.contains(H2Error.Code.CANCEL), probeOk)
              } finally probe.close()
            } finally client.close()
          }
        }.flatMap { result =>
          ZIO.attemptBlocking(awaitThreadDrain(before)).map(drained => result && assertTrue(drained))
        }
      },
      test("event larger than maxFrameSize survives framing across DATA frames") {
        val big  = ServerSentEvent("z" * 40000)
        val wire = new String(SseCodec.encode(big).toArray[Byte], Utf8)
        withRawServer(sseRoutes(Stream.fromIterable(List(big)))) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true, acceptSse = true))
              client.awaitHeaders(1)
              client.readSseData(1, minFrames = 2) { (_, frames, endSeen) =>
                val actual = frames.map(b => new String(b.toArray[Byte], Utf8)).mkString
                println(
                  s"[SseDelayIntegrationSpec] large-event-proof frames=${frames.length} bytes=${actual.length}",
                )
                assertTrue(wire.length > 16384, endSeen, actual == wire)
              }
            } finally client.close()
          }
        }
      },
    ) @@ sequential

  private def withRawServer[R](
    routes: Routes[Any],
    http2Config: Http2Config = Http2Config(),
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(
          ServerHandle.live(
            List(
              new H2Transport(
                routes,
                Context.empty,
                Connector(
                  bind = BindAddress.localhost(0),
                  protocol = Protocol.H2C(http2Config),
                  idleTimeout = java.time.Duration.ofSeconds(60),
                ),
                DefectHandler.default,
              ).start(),
            ),
          ),
        ),
      )(h => ZIO.succeed(h.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, p) => p
          case other                  => throw new AssertionError("Expected TCP: " + other)
        }
        use(port)
      }

  private def countStreamThreads(): Int = {
    // Virtual threads are included in getAllStackTraces.
    var count = 0
    val it    = Thread.getAllStackTraces().keySet().iterator()
    while (it.hasNext) {
      val t = it.next()
      if (t.getName.startsWith("zio-http-h2-stream-")) count += 1
    }
    count
  }

  private def awaitThreadDrain(baseline: Int, deadlineMs: Long = 15000L): Boolean = {
    val deadline = java.lang.System.currentTimeMillis() + deadlineMs
    var drained  = countStreamThreads() <= baseline
    while (!drained && java.lang.System.currentTimeMillis() < deadline) {
      Thread.sleep(200)
      drained = countStreamThreads() <= baseline
    }
    val after    = countStreamThreads()
    println(s"[SseDelayIntegrationSpec] thread-receipt before=$baseline after=$after drained=$drained")
    drained
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

    def makeHeaders(
      method: String,
      path: String,
      streamId: Int,
      endStream: Boolean,
      acceptSse: Boolean = false,
    ): Headers =
      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(
          List(
            HeaderField(":method", method),
            HeaderField(":path", path),
            HeaderField(":scheme", "http"),
            HeaderField(":authority", s"127.0.0.1:$port"),
          ) ++ (if (acceptSse) List(HeaderField("accept", "text/event-stream")) else Nil),
        ),
        endStream = endStream,
        endHeaders = true,
      )

    def awaitHeaders(streamId: Int): List[HeaderField] = {
      val hdrs = mutable.ListBuffer.empty[HeaderField]
      var done = false
      while (!done)
        readFrame() match {
          case Settings(false, _)                                 => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                  => ()
          case _: WindowUpdate                                    => ()
          case _: Ping                                            => ()
          case Headers(sid, block, _, _, _, _) if sid == streamId =>
            decoder.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            done = true
          case _: Headers | _: Continuation | _: Priority         => ()
          case _: Data | _: RstStream | _: GoAway                 => ()
          case other if other.streamId == streamId                =>
            throw new AssertionError("Unexpected frame before HEADERS: " + other)
          case _                                                  => ()
        }
      hdrs.toList
    }

    /**
     * Incremental SSE read: consumes H2 DATA frames one at a time (never a
     * buffered whole-body read), timestamps each non-empty frame arrival with
     * `nanoTime`, tops up windows per RFC 9113 6.9, and collects per-frame
     * inter-arrival gaps. Hands `(gapsMs, frames, endSeen)` to `check` so every
     * test asserts on incrementally-observed evidence.
     */
    def readSseData(
      streamId: Int,
      minFrames: Int = 0,
    )(check: (List[Long], List[Chunk[Byte]], Boolean) => TestResult): TestResult = {
      val gaps    = mutable.ListBuffer.empty[Long]
      val frames  = mutable.ListBuffer.empty[Chunk[Byte]]
      var lastNs  = 0L
      var endSeen = false
      var done    = false
      while (!done)
        readFrame() match {
          case Settings(false, _)                             => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                              => ()
          case _: WindowUpdate                                => ()
          case _: Ping                                        => ()
          case Headers(sid, _, _, _, _, _) if sid == streamId => ()
          case Data(sid, data, end, _) if sid == streamId     =>
            if (data.nonEmpty) {
              val nowNs = java.lang.System.nanoTime()
              if (lastNs != 0L) gaps += ((nowNs - lastNs) / 1000000L)
              lastNs = nowNs
              frames += data
              topUp(streamId, data.length)
            }
            if (end) { endSeen = true; done = true }
          case _: Continuation | _: Priority                  => ()
          case RstStream(sid, _) if sid == streamId           =>
            throw new AssertionError("Stream reset before END_STREAM")
          case _: RstStream | _: GoAway | _: Data             => ()
          case other if other.streamId == streamId            =>
            throw new AssertionError("Unexpected frame for stream: " + other)
          case _                                              => ()
        }
      println(
        s"[SseDelayIntegrationSpec] incremental-proof frames=${frames.length} gapsMs=${gaps.toList} endSeen=$endSeen",
      )
      assertTrue(frames.length >= minFrames) && check(gaps.toList, frames.toList, endSeen)
    }

    def awaitRst(streamId: Int, timeoutMs: Long): Option[H2Error.Code] = {
      val deadline                     = java.lang.System.currentTimeMillis() + timeoutMs
      socket.setSoTimeout(2000)
      var result: Option[H2Error.Code] = None
      while (result.isEmpty && java.lang.System.currentTimeMillis() < deadline)
        try
          readFrame() match {
            case RstStream(sid, code) if sid == streamId  => result = Some(code)
            case Data(sid, data, _, _) if sid == streamId => topUp(sid, data.length)
            case _                                        => ()
          }
        catch { case _: SocketTimeoutException => () }
      socket.setSoTimeout(20000)
      result
    }

    override def close(): Unit = socket.close()

    /**
     * Replenish the sender's connection- and stream-level windows (RFC 9113
     * 6.9).
     */
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
}
