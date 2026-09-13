package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

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
  BindAddress,
  BoundAddress,
  Connector,
  Handler,
  LoomServer,
  Request,
  Response,
  Route,
  Routes,
  Status,
  handler,
}

/**
 * Slow-stream / slow-loris deadlines for the H2 server transport.
 *
 * A peer that stalls header completion or drips body bytes slowly must not hold
 * server resources indefinitely. Time-to-complete is the metric: flow-control
 * windows alone are not sufficient.
 *
 *   - an incomplete header block (HEADERS without END_HEADERS, CONTINUATION
 *     never arrives) past `headerTimeoutMs` is reset with `RST_STREAM(CANCEL)`
 *     while the connection stays usable for sibling streams;
 *   - a body that keeps moving but too slowly (1 byte per 500ms, total past
 *     `bodyTimeoutMs`) is reset with `RST_STREAM(CANCEL)` even though every
 *     inter-frame gap is well under the deadline;
 *   - a healthy slow-but-moving stream that completes under the deadline passes
 *     with 200;
 *   - the whole-request deadline is wired from `Connector.requestTimeoutMs` (a
 *     stalled handler stream is reset; fast streams are untouched);
 *   - a deadline reset never emits HEADERS after the RST.
 */
object H2SlowStreamSpec extends ZIOSpecDefault {

  private val HeaderTimeoutMs: Long  = 1000L
  private val BodyTimeoutMs: Long    = 2000L
  private val RequestTimeoutMs: Long = 30000L

  /**
   * Socket read timeout: headroom above every explicit await deadline below.
   */
  private val SoTimeoutMs: Int = 20000

  /**
   * Quiet window after a deadline RST during which no stream frame may arrive.
   */
  private val QuietWindowMs: Int = 2500

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2SlowStreamSpec")(
      test("stalled header block past deadline is reset with CANCEL and connection stays usable") {
        withServer(headerTimeoutMs = HeaderTimeoutMs, bodyTimeoutMs = BodyTimeoutMs) { port =>
          ZIO.attemptBlocking {
            val client = new SlowTestClient(port)
            try {
              client.sendSplitHeaders(streamId = 1)
              val code    = client.awaitReset(streamId = 1, timeoutMs = 8000)
              val sibling = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(code == H2Error.Code.CANCEL, sibling.status == 200)
            } finally client.close()
          }
        }
      },
      test("slow body drip past time-to-complete is reset with CANCEL though gaps stay under deadline") {
        withServer(headerTimeoutMs = HeaderTimeoutMs, bodyTimeoutMs = BodyTimeoutMs) { port =>
          ZIO.attemptBlocking {
            val client = new SlowTestClient(port)
            try {
              // 7 bytes x 500ms gaps = ~3s total vs 2s time-to-complete
              // deadline: 1s margin, every 500ms gap well under the deadline.
              val code = client.postDrip(streamId = 1, totalBytes = 7, gapMs = 500L)
              assertTrue(code == H2Error.Code.CANCEL)
            } finally client.close()
          }
        }
      },
      test("healthy slow-but-moving body under deadline passes with 200") {
        withServer(headerTimeoutMs = HeaderTimeoutMs, bodyTimeoutMs = BodyTimeoutMs) { port =>
          ZIO.attemptBlocking {
            val client = new SlowTestClient(port)
            try {
              val response = client.postDripExpectResponse(streamId = 1, totalBytes = 5, gapMs = 50L)
              assertTrue(response.status == 200, response.body.length == 5)
            } finally client.close()
          }
        }
      },
      test("whole-request deadline from Connector.requestTimeoutMs resets a stalled handler stream") {
        withServer(headerTimeoutMs = 5000L, bodyTimeoutMs = 30000L, requestTimeoutMs = 800L, routes = SlowRoutes) {
          port =>
            ZIO.attemptBlocking {
              val client = new SlowTestClient(port)
              try {
                // Complete request: the handler itself stalls past the 800ms
                // whole-request deadline, so the stream must be reset.
                val code = client.postExpectingReset(streamId = 1, path = "/", body = Chunk.empty)
                assertTrue(code == H2Error.Code.CANCEL)
              } finally client.close()
            }
        }
      },
      test("configured request timeout does not fire on fast streams") {
        withServer(headerTimeoutMs = 5000L, bodyTimeoutMs = 30000L, requestTimeoutMs = 2000L) { port =>
          ZIO.attemptBlocking {
            val client = new SlowTestClient(port)
            try {
              val body     = Chunk.fromArray(Array.fill(16)(0x41.toByte))
              val response = client.postExpectingResponse(streamId = 1, path = "/", body = body)
              assertTrue(response.status == 200, response.body == body)
            } finally client.close()
          }
        }
      },
      test("timed-out drip sends no HEADERS after the RST") {
        withServer(headerTimeoutMs = HeaderTimeoutMs, bodyTimeoutMs = BodyTimeoutMs) { port =>
          ZIO.attemptBlocking {
            val client = new SlowTestClient(port)
            try {
              // One byte of a declared 8-byte body, then stall past the 2s
              // time-to-complete body deadline: the server must reset with
              // CANCEL and never answer with a (bogus 500) HEADERS.
              client.sendFrame(client.makeHeaders("POST", "/", streamId = 1, endStream = false, Some("8")))
              client.sendFrame(Data(1, Chunk.single(0x41.toByte), endStream = false))
              val resetCode = client.awaitRstStrict(streamId = 1, expected = H2Error.Code.CANCEL, timeoutMs = 15000)
              client.assertStreamQuiet(streamId = 1, graceMs = QuietWindowMs)
              assertTrue(resetCode == H2Error.Code.CANCEL)
            } finally client.close()
          }
        }
      },
    ) @@ sequential

  private val EchoRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern.POST,
        handler { (req: Request) =>
          responseAsResult(Response(status = Status.Ok, body = req.body))
        },
      ),
      Route(RoutePattern.GET, Handler.succeed(Response.ok)),
    )

  /**
   * Stalled-handler routes: every POST sleeps past any test `requestTimeoutMs`,
   * so the whole-request deadline is the only thing that can complete the
   * stream. The sleep runs on the stream's virtual thread, never a ZIO fiber.
   */
  private val SlowRoutes: Routes[Any] =
    Routes(
      Route(
        RoutePattern.POST,
        handler { (_: Request) =>
          Thread.sleep(6000L)
          responseAsResult(Response.ok)
        },
      ),
      Route(RoutePattern.GET, Handler.succeed(Response.ok)),
    )

  private def withServer[R](
    headerTimeoutMs: Long,
    bodyTimeoutMs: Long,
    requestTimeoutMs: Long = RequestTimeoutMs,
    routes: Routes[Any] = EchoRoutes,
  )(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(
            bind = BindAddress.localhost(0),
            headerTimeoutMs = headerTimeoutMs,
            bodyTimeoutMs = bodyTimeoutMs,
            requestTimeoutMs = requestTimeoutMs,
          )
          new LoomServer(connector).serve(routes, Context.empty)
        },
      )(handle => ZIO.attemptBlocking(handle.shutdownAndWait()).ignore)
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte])

  private final class SlowTestClient(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(SoTimeoutMs)
    private val out   = socket.getOutputStream
    private val rawIn = socket.getInputStream
    private var buf   = Chunk.empty[Byte]

    private val encoder = new HpackEncoder()
    private val decoder = new HpackDecoder()

    private val frameBuffer: mutable.Map[Int, mutable.Queue[H2Frame]] =
      mutable.Map.empty

    handshake()

    def sendFrame(frame: H2Frame): Unit   = sendRaw(FrameCodec.encode(frame).toArray)
    def sendRaw(bytes: Array[Byte]): Unit = { out.write(bytes); out.flush() }

    /**
     * Sends an empty first fragment with END_HEADERS=false and never sends the
     * CONTINUATION: the server must reset the stream after `headerTimeoutMs`.
     * The fragment is empty (no HPACK bytes) so the shared HPACK dynamic table
     * stays in sync and the connection remains usable for sibling streams after
     * the reset.
     */
    def sendSplitHeaders(streamId: Int): Unit =
      sendFrame(Headers(streamId, Chunk.empty, endStream = true, endHeaders = false))

    /**
     * POSTs `totalBytes` one byte at a time with `gapMs` between DATA frames,
     * then waits for the timeout reset. Used for the slow-drip case.
     *
     * A write may fail with `SocketException` if it races the server's reset:
     * the bytes already sent still prove the drip, and the reset itself is
     * asserted below via `awaitReset` (reads are unaffected). A server that
     * kills the connection *instead* of resetting surfaces as GOAWAY or a
     * wait-timeout there and still fails loudly.
     */
    def postDrip(streamId: Int, totalBytes: Int, gapMs: Long): H2Error.Code = {
      sendFrame(makeHeaders("POST", "/", streamId, endStream = false, Some(totalBytes.toString)))
      var sent        = 0
      var writeFailed = false
      while (sent < totalBytes && !writeFailed) {
        val last = sent == totalBytes - 1
        try sendFrame(Data(streamId, Chunk.single(0x41.toByte), endStream = last))
        catch {
          case _: java.net.SocketException => writeFailed = true
        }
        sent += 1
        if (!last && !writeFailed) Thread.sleep(gapMs)
      }
      awaitReset(streamId, timeoutMs = 15000)
    }

    /** Same drip but expects a full response (healthy slow stream). */
    def postDripExpectResponse(streamId: Int, totalBytes: Int, gapMs: Long): RawResponse = {
      sendFrame(makeHeaders("POST", "/", streamId, endStream = false, Some(totalBytes.toString)))
      var sent = 0
      while (sent < totalBytes) {
        val last = sent == totalBytes - 1
        sendFrame(Data(streamId, Chunk.single(0x41.toByte), endStream = last))
        sent += 1
        if (!last) Thread.sleep(gapMs)
      }
      awaitResponseOrReset(streamId) match {
        case Left(response) => response
        case Right(code)    => throw new AssertionError("Expected 200 but stream was reset: " + code)
      }
    }

    /** Complete POST that must be reset (stalled-handler case). */
    def postExpectingReset(streamId: Int, path: String, body: Chunk[Byte]): H2Error.Code = {
      sendFrame(makeHeaders("POST", path, streamId, body.isEmpty, None))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitReset(streamId, timeoutMs = 15000)
    }

    /** Complete POST that must answered with a full echo response. */
    def postExpectingResponse(streamId: Int, path: String, body: Chunk[Byte]): RawResponse = {
      sendFrame(makeHeaders("POST", path, streamId, body.isEmpty, None))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponseOrReset(streamId) match {
        case Left(response) => response
        case Right(code)    => throw new AssertionError("Expected a full response but reset: " + code)
      }
    }

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse =
      awaitResponseOrReset(
        streamId,
        sendFirst = Some(() => {
          sendFrame(makeHeaders(method, path, streamId, body.isEmpty, None))
          if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
        }),
      ) match {
        case Left(response) => response
        case Right(code)    => throw new AssertionError("Expected a full response but reset: " + code)
      }

    def makeHeaders(
      method: String,
      path: String,
      streamId: Int,
      endStream: Boolean,
      declaredLength: Option[String],
    ): Headers = {
      val pseudo = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      )
      val length = declaredLength.toList.map(value => HeaderField("content-length", value))
      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(pseudo ++ length),
        endStream = endStream,
        endHeaders = true,
      )
    }

    /**
     * Waits for RST_STREAM, tolerating a best-effort error response the handler
     * may emit after the reset (either frame may hit the wire first).
     *
     * Polls with a short socket timeout so a hung server fails here with an
     * explicit deadline message instead of surfacing as a raw
     * `SocketTimeoutException` from deep inside `readFrame`.
     */
    def awaitReset(streamId: Int, timeoutMs: Int): H2Error.Code = {
      socket.setSoTimeout(1000)
      try {
        val deadline                     = java.lang.System.currentTimeMillis() + timeoutMs
        var result: Option[H2Error.Code] = None
        while (result.isEmpty) {
          if (java.lang.System.currentTimeMillis() > deadline)
            throw new AssertionError("Timed out waiting for RST_STREAM on stream " + streamId)
          try {
            nextFrameFor(streamId) match {
              case RstStream(_, code)   => result = Some(code)
              case _: Headers           => () // Best-effort 500 on the dead stream; keep waiting for the RST.
              case _: Data              => ()
              case GoAway(_, code, dbg) =>
                throw new AssertionError(s"GOAWAY while waiting RST: $code ${new String(dbg.toArray)}")
              case _                    => ()
            }
          } catch {
            case _: SocketTimeoutException => ()
          }
        }
        result.get
      } finally socket.setSoTimeout(SoTimeoutMs)
    }

    /**
     * Waits for the expected RST_STREAM and returns its code. Any HEADERS (e.g.
     * a best-effort 500 sent after the reset) or DATA on the stream fails
     * immediately.
     */
    def awaitRstStrict(streamId: Int, expected: H2Error.Code, timeoutMs: Int): H2Error.Code = {
      socket.setSoTimeout(1000)
      try {
        val deadline = java.lang.System.currentTimeMillis() + timeoutMs
        var done     = false
        while (!done) {
          if (java.lang.System.currentTimeMillis() > deadline)
            throw new AssertionError("Timed out waiting for RST_STREAM on stream " + streamId)
          try {
            nextFrameFor(streamId) match {
              case RstStream(_, code)        =>
                if (code != expected)
                  throw new AssertionError("Expected RST_STREAM " + expected + " but got " + code)
                done = true
              case Headers(_, _, _, _, _, _) =>
                throw new AssertionError(
                  "HEADERS arrived on stream " + streamId + " awaiting RST_STREAM (no HEADERS may follow an RST)",
                )
              case Data(_, _, _, _)          =>
                throw new AssertionError("DATA arrived on stream " + streamId + " awaiting RST_STREAM")
              case GoAway(_, code, dbg)      =>
                throw new AssertionError(s"GOAWAY while waiting RST: $code ${new String(dbg.toArray)}")
              case _                         => ()
            }
          } catch {
            case _: SocketTimeoutException => ()
          }
        }
        expected
      } finally socket.setSoTimeout(SoTimeoutMs)
    }

    /**
     * After the RST, no HEADERS/DATA for the stream may arrive: connection-
     * level frames are drained and ignored, repeat RSTs are tolerated (the
     * virtual-timer and poll paths can both fire CANCEL without changing reset
     * semantics), but anything stream-addressed beyond that fails.
     *
     * If the server reaps this already-reset connection mid-window (EOF with no
     * GOAWAY), that is not a HEADERS violation: TCP in-order delivery
     * guarantees any post-RST HEADERS would have been decoded before the FIN,
     * so reaching EOF proves none arrived. The window then reduces to proving
     * the server itself is still healthy via a fresh connection.
     */
    def assertStreamQuiet(streamId: Int, graceMs: Int): Unit = {
      socket.setSoTimeout(400)
      try {
        val deadline = java.lang.System.currentTimeMillis() + graceMs
        while (java.lang.System.currentTimeMillis() < deadline) {
          try {
            readFrame() match {
              case Headers(sid, _, _, _, _, _) if sid == streamId =>
                throw new AssertionError("HEADERS arrived on stream " + sid + " after its RST_STREAM")
              case Data(sid, _, _, _) if sid == streamId          =>
                throw new AssertionError("DATA arrived on stream " + sid + " after its RST_STREAM")
              case RstStream(sid, _) if sid == streamId           => ()
              case _                                              => ()
            }
          } catch {
            case _: SocketTimeoutException => ()
            case _: EOFException           =>
              verifyServerAlive()
              return
          }
        }
      } finally socket.setSoTimeout(SoTimeoutMs)
    }

    /**
     * Fresh-connection liveness probe: the server must still serve requests.
     */
    private def verifyServerAlive(): Unit = {
      val probe = new SlowTestClient(port)
      try {
        probe.sendFrame(probe.makeHeaders("GET", "/", streamId = 1, endStream = true, None))
        probe.nextFrameFor(1) match {
          case Headers(_, _, _, _, _, _) => ()
          case RstStream(_, code)        => throw new AssertionError("liveness probe reset: " + code)
          case GoAway(_, code, dbg)      =>
            throw new AssertionError(s"liveness probe GOAWAY: $code ${new String(dbg.toArray)}")
          case _                         => throw new AssertionError("unexpected liveness probe response")
        }
      } finally probe.close()
    }

    private def awaitResponseOrReset(
      streamId: Int,
      sendFirst: Option[() => Unit] = None,
    ): Either[RawResponse, H2Error.Code] = {
      sendFirst.foreach(_())
      val hdrs   = mutable.ListBuffer.empty[HeaderField]
      var body   = Chunk.empty[Byte]
      var done   = false
      while (!done) {
        nextFrameFor(streamId) match {
          case Headers(_, block, end, _, _, _) =>
            decoder.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            if (end) done = true
          case Data(_, data, end, _)           =>
            body = body ++ data
            if (end) done = true
          case RstStream(_, code)              =>
            return Right(code)
          case GoAway(_, code, dbg)            =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                               =>
            throw new AssertionError("Unexpected frame while waiting for response")
        }
      }
      val status = hdrs
        .find(_.name == ":status")
        .map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      Left(RawResponse(status, hdrs.toList, body))
    }

    private def nextFrameFor(streamId: Int): H2Frame = {
      frameBuffer.get(streamId) match {
        case Some(queue) if queue.nonEmpty => return queue.dequeue()
        case _                             => ()
      }
      while (true) {
        readFrame() match {
          case Settings(false, _)                  => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                   => ()
          case _: WindowUpdate                     => ()
          case Ping(false, data)                   =>
            sendFrame(Ping(ack = true, data))
          case frame if frame.streamId == streamId => return frame
          case other                               =>
            val queue = frameBuffer.getOrElseUpdate(other.streamId, mutable.Queue.empty)
            queue.enqueue(other)
        }
      }
      throw new AssertionError("unreachable")
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
      readFrame() // consume server's ACK for our SETTINGS
    }
  }
}
