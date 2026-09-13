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
import zio.http.h2.hpack.{HeaderField, Hpack, HpackDecoder, HpackEncoder}
import zio.http.ResultType._
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Http2Config,
  Method,
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
 * Todo 6: the load-bearing streaming fix. `H2Transport.sendResponse` must
 * consume `response.body.toStream` chunk-by-chunk — each chunk gated by
 * `FlowController.consumeSendWindow` (which parks the Loom virtual thread on a
 * Condition, never a ZIO fiber), sliced by `maxFrameSize`, DATA frames written
 * under the shared writeLock — instead of `toChunk` materialization.
 *
 * jvm-perf lens: the hot loop is allocation-bounded (one maxFrameSize chunk in
 * flight), makes no per-frame megamorphic calls (final FlowController, single
 * MuxStream impl), and boxes nothing (primitive Int lengths).
 */
object StreamingBodySpec extends ZIOSpecDefault {

  private val BodyBytes10M = 10 * 1024 * 1024

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

  private def checksum(total: Int): Long = {
    var sum = 0L
    var i   = 0
    while (i < total) {
      sum += ((i % 251) & 0xff).toLong
      i += 1
    }
    sum
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("StreamingBodySpec")(
      test("unknown-length 10MB body streams without materializing (peak-heap accounting)") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(BodyBytes10M))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            resetHeapPeaks()
            val client = new RawH2Client(port)
            try {
              val counted = client.getCounted("/", streamId = 1)
              val peak    = peakHeapBytes()
              println(
                s"[StreamingBodySpec] heap-proof bytes=${counted.totalBytes} checksum=${counted.checksum} " +
                  s"expectedChecksum=${checksum(BodyBytes10M)} peakHeapBytes=$peak dataFrames=${counted.dataFrames}",
              )
              assertTrue(
                counted.status == 200,
                counted.totalBytes == BodyBytes10M.toLong,
                counted.checksum == checksum(BodyBytes10M),
                counted.dataFrames > 1,
                // Materializing 10MB via toChunk would spike the heap by >= 10MB
                // (body chunk) plus builder-growth transient. Streaming keeps one
                // maxFrameSize chunk in flight: peak must stay far below 10MB.
                peak < 5L * 1024L * 1024L,
              )
            } finally client.close()
          }
        }
      },
      test("unknown-length body omits content-length; known-length body sets it") {
        val knownBytes   = Chunk.fromArray("known-content".getBytes(StandardCharsets.UTF_8))
        val unknownTotal = 4096
        val routes       = Routes(
          // knownChunk present: Content-Length must be emitted
          Route(
            RoutePattern.POST,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromChunk(knownBytes)))
            },
          ),
          // knownChunk absent but knownLength present: Content-Length must be emitted
          Route(
            RoutePattern.PUT,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(Stream.fromChunk(knownBytes).map(b => b))),
              )
            },
          ),
          // truly unknown length: no Content-Length (stream until END_STREAM)
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(unknownTotal))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // Sanity: the unknown fixture really has no known chunk/length
              val witness  = unfoldingBytes(8)
              assertTrue(witness.knownChunk.isEmpty, witness.knownLength.isEmpty)
              val unknown  = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              val known    = client.roundTrip("POST", "/", Chunk.empty, streamId = 3)
              val knownLen = client.roundTrip("PUT", "/", Chunk.empty, streamId = 5)
              println(
                s"[StreamingBodySpec] content-length unknown=${unknown.headerValue("content-length")} " +
                  s"known=${known.headerValue("content-length")} knownlen=${knownLen.headerValue("content-length")}",
              )
              assertTrue(
                unknown.status == 200,
                unknown.body.length == unknownTotal,
                unknown.headerValue("content-length").isEmpty,
                known.status == 200,
                known.body == knownBytes,
                known.headerValue("content-length").contains(knownBytes.length.toString),
                knownLen.status == 200,
                knownLen.body == knownBytes,
                knownLen.headerValue("content-length").contains(knownBytes.length.toString),
              )
            } finally client.close()
          }
        }
      },
      test("DATA frames respect maxFrameSize for unknown-length bodies") {
        val total  = 100 * 1024 // spans several 16KB frames
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withRawServer(routes, http2Config = Http2Config(maxFrameSize = 16384)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val counted = client.getCounted("/", streamId = 1)
              println(
                s"[StreamingBodySpec] frame-size-proof frames=${counted.dataFrames} maxData=${counted.maxData} " +
                  s"total=${counted.totalBytes}",
              )
              assertTrue(
                counted.status == 200,
                counted.totalBytes == total.toLong,
                counted.checksum == checksum(total),
                counted.dataFrames > 1,
                counted.maxData <= 16384,
                counted.maxData > 0,
              )
            } finally client.close()
          }
        }
      },
      test("WINDOW_UPDATE unblocks the streaming writer") {
        val total  = 2000
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withRawServer(routes, http2Config = Http2Config(initialWindowSize = 100)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))

              var sawHeaders = false
              var stalled    = true
              client.socket.setSoTimeout(1000)
              try
                while (true)
                  client.readFrame() match {
                    case Headers(1, _, false, _, _, _) => sawHeaders = true
                    case Data(1, _, _, _)              => stalled = false
                    case _: WindowUpdate | _: Settings => ()
                    case _: Ping                       => ()
                    case other                         => throw new AssertionError("Unexpected frame: " + other)
                  }
              catch { case _: SocketTimeoutException => () }

              // 2000 bytes >> 100-byte stream window: HEADERS (not flow-controlled)
              // arrive, DATA blocks on the FlowController Condition (parks, no spin).
              assertTrue(sawHeaders, stalled)

              client.socket.setSoTimeout(20000)
              client.sendFrame(WindowUpdate(streamId = 1, increment = 65535))

              var bytes           = 0L
              var sum             = 0L
              var done            = false
              var unblockedFrames = 0
              while (!done)
                client.readFrame() match {
                  case Data(1, data, end, _)         =>
                    var i = 0
                    while (i < data.length) {
                      sum += (data(i) & 0xff).toLong
                      i += 1
                    }
                    bytes += data.length
                    unblockedFrames += 1
                    done = end
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              println(
                s"[StreamingBodySpec] window-proof stalled=$stalled unblockedFrames=$unblockedFrames bytes=$bytes",
              )
              assertTrue(bytes == total.toLong, sum == checksum(total), unblockedFrames > 0)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("HEAD serves a streaming route with an empty body") {
        val total  = 1024 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val head = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 1)
              val get  = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              println(s"[StreamingBodySpec] head-proof headBytes=${head.body.length} getBytes=${get.body.length}")
              assertTrue(
                head.status == 200,
                head.body.isEmpty,
                get.status == 200,
                get.body.length == total,
              )
            } finally client.close()
          }
        }
      },
      test("mid-stream client RST triggers server RST_STREAM(CANCEL); double RST is idempotent") {
        // Throttled so the server is guaranteed mid-flight when the client aborts:
        // 8MB paced at ~10ms per 128KB keeps the writer busy for seconds.
        val total  = 8 * 1024 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total, 128 * 1024, 10L))),
              )
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              // Wait for the first DATA: proves the server started streaming.
              // Top up windows like a real receiver so the 8MB flight never
              // stalls the server's connection-level FlowController.
              var firstData = false
              client.socket.setSoTimeout(20000)
              while (!firstData)
                client.readFrame() match {
                  case Headers(1, _, false, _, _, _) => ()
                  case Data(1, data, false, _)       =>
                    client.topUp(1, data.length)
                    firstData = true
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(firstData)

              // Abort mid-stream, twice: the second RST must be a tolerated no-op.
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              var sawCancel = false
              val deadline  = java.lang.System.currentTimeMillis() + 20000L
              while (!sawCancel && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case RstStream(1, code)                                =>
                    sawCancel = code == H2Error.Code.CANCEL
                    println(s"[StreamingBodySpec] cancel-proof serverRst=$code")
                  case Data(1, data, _, _)                               =>
                    client.topUp(1, data.length)
                  case _: Data | _: WindowUpdate | _: Settings | _: Ping => ()
                  case _: Headers | _: GoAway | _: Continuation          => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(sawCancel)
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))

              // The connection must stay usable: a fresh stream round-trips fine
              // (HEAD keeps the check cheap: the route streams 8MB on GET).
              val next = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 3)
              println(s"[StreamingBodySpec] cancel-proof reuseStatus=${next.status} reuseBytes=${next.body.length}")
              assertTrue(next.status == 200)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("server stream threads are released after abort") {
        val total  = 8 * 1024 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total, 128 * 1024, 10L))),
              )
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val before           = countStreamThreads()
            val client           = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              var firstData = false
              client.socket.setSoTimeout(20000)
              while (!firstData)
                client.readFrame() match {
                  case Data(1, data, false, _)                              =>
                    client.topUp(1, data.length)
                    firstData = true
                  case _: Headers | _: WindowUpdate | _: Settings | _: Ping => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              // Drain until the server's CANCEL echo, then close: the stream
              // handler must unwind (request-timer cancel + flow deregistration
              // in `finally`) and its virtual thread must die.
              val deadline  = java.lang.System.currentTimeMillis() + 20000L
              var sawRst    = false
              while (!sawRst && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case RstStream(1, _)                                   => sawRst = true
                  case Data(1, data, _, _)                               => client.topUp(1, data.length)
                  case _: Data | _: WindowUpdate | _: Settings | _: Ping => ()
                  case _: Headers | _: GoAway | _: Continuation          => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(sawRst)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
            val releasedDeadline = java.lang.System.currentTimeMillis() + 15000L
            var after            = countStreamThreads()
            while (after > before && java.lang.System.currentTimeMillis() < releasedDeadline) {
              Thread.sleep(100L)
              after = countStreamThreads()
            }
            println(s"[StreamingBodySpec] thread-proof before=$before after=$after")
            assertTrue(after <= before)
          }
        }
      },
    ) @@ sequential

  // ─── helpers ──────────────────────────────────────────────────────────────

  private def resetHeapPeaks(): Unit = {
    java.lang.System.gc()
    Thread.sleep(200L)
    val pools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans
    val it    = pools.iterator()
    while (it.hasNext) {
      val pool = it.next()
      try pool.resetPeakUsage()
      catch { case _: UnsupportedOperationException => () }
    }
    // Live old-gen floor after the full GC: the framework/runtime footprint
    // that the transfer-time peak must be measured against.
    oldGenBaseline = oldGenUsedBytes()
  }

  private def peakHeapBytes(): Long = {
    // Old-gen only, deliberately: short-lived streaming buffers (16KB chunks,
    // socket reads) die in eden, while a materialized 10MB body is retained
    // for the whole transfer, survives young GCs, and tenures into old-gen.
    // Total-heap peak cannot discriminate (any workload that fills eden peaks
    // at eden capacity). No gc() here: a full GC after the transfer would
    // promote dying young garbage into old and inflate the peak after the fact.
    oldGenPeakBytes() - oldGenBaseline
  }

  /** Old-gen usage right after [[resetHeapPeaks]]' full GC: the live floor. */
  private var oldGenBaseline: Long = 0L

  private def oldGenPeakBytes(): Long = {
    var peak = 0L
    val it   = oldGenPools().iterator
    while (it.hasNext) peak += it.next().getPeakUsage.getUsed
    peak
  }

  private def oldGenUsedBytes(): Long = {
    var used = 0L
    val it   = oldGenPools().iterator
    while (it.hasNext) used += it.next().getUsage.getUsed
    used
  }

  private def oldGenPools(): List[java.lang.management.MemoryPoolMXBean] = {
    val out = List.newBuilder[java.lang.management.MemoryPoolMXBean]
    val it  = java.lang.management.ManagementFactory.getMemoryPoolMXBeans.iterator()
    while (it.hasNext) {
      val pool = it.next()
      val name = pool.getName
      if (
        pool.getType == java.lang.management.MemoryType.HEAP &&
        (name.contains("Old") || name.contains("Tenured"))
      ) out += pool
    }
    out.result()
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
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C(http2Config)),
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

    /**
     * Like [[awaitResponse]] but discards body bytes (checksum only) so the
     * client's own heap stays flat: any heap spike measured around this call
     * belongs to the server path under test, not the test client.
     */
    def getCounted(path: String, streamId: Int): CountedResponse = {
      sendFrame(makeHeaders("GET", path, streamId, endStream = true))
      val hdrs       = mutable.ListBuffer.empty[HeaderField]
      var totalBytes = 0L
      var sum        = 0L
      var dataFrames = 0
      var maxData    = 0
      var status     = -1
      var done       = false
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
            status = hdrs.find(_.name == ":status").map(_.value.toInt).getOrElse(-1)
            done = end
          case Data(sid, data, end, _) if sid == streamId           =>
            var i = 0
            while (i < data.length) {
              sum += (data(i) & 0xff).toLong
              i += 1
            }
            totalBytes += data.length
            dataFrames += 1
            if (data.length > maxData) maxData = data.length
            noteReceived(sid, data.length)
            done = end
          case GoAway(_, code, dbg)                                 =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _: RstStream | _: Continuation | _: Priority         => ()
          case other if other.streamId == streamId                  =>
            throw new AssertionError("Unexpected frame for stream: " + other)
          case _                                                    => ()
        }
      }
      if (status < 0) throw new AssertionError("Missing :status")
      CountedResponse(status, hdrs.toList, totalBytes, sum, dataFrames, maxData)
    }

    override def close(): Unit = socket.close()

    /**
     * A real HTTP/2 receiver replenishes the sender's windows as it consumes
     * DATA (RFC 9113 6.9): without this, any transfer larger than the 64KB
     * default connection window stalls the server's FlowController forever.
     * Disabled only for the test that asserts the stall itself.
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

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte]) {
    def headerValue(name: String): Option[String] = headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
  }

  private final case class CountedResponse(
    status: Int,
    headers: List[HeaderField],
    totalBytes: Long,
    checksum: Long,
    dataFrames: Int,
    maxData: Int,
  ) {
    def headerValue(name: String): Option[String] = headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
  }
}
