package zio.http.h2

import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets

import scala.annotation.experimental
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
 * Todo 7: streaming edge cases — HEAD, cancellation, GOAWAY interleaving,
 * flow-control exhaustion.
 *
 * Every transfer larger than 64KB tops up connection/stream windows per RFC
 * 9113 section 6.9 (see [[RawH2Client.topUp]]); without it the server's
 * FlowController parks forever and the test — not the server — is at fault.
 *
 * jvm-perf lens: no new hot paths here. The bounded flow-control wait stays a
 * Condition park (no spin), lengths stay primitive Ints (no boxing), and all
 * RST paths reuse the single T5 `sendRstStream` send site (monomorphic).
 */
@experimental
object StreamingEdgeSpec extends ZIOSpecDefault {

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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("StreamingEdgeSpec")(
      test("HEAD on streaming and known bodies returns headers only, zero DATA frames") {
        val streamedTotal = 1024 * 1024
        val knownBytes    = Chunk.fromArray(new Array[Byte](256 * 1024))
        val streaming     = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(streamedTotal))))
            },
          ),
        )
        val known         = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromChunk(knownBytes)))
            },
          ),
        )
        val streamed      = withRawServer(streaming) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              val streamedData = headAndCountData(client, 1)
              println(s"[StreamingEdgeSpec] head-proof streamedData=$streamedData")
              assertTrue(streamedData == 0)
            } finally client.close()
          }
        }
        val knownRun      = withRawServer(known) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              val knownData = headAndCountData(client, 1)
              println(s"[StreamingEdgeSpec] head-proof knownData=$knownData")
              assertTrue(knownData == 0)
            } finally client.close()
          }
        }
        streamed.zipPar(knownRun).map { case (a, b) => a && b }
      },
      test("double client cancel yields exactly one server RST_STREAM(CANCEL)") {
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

              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              var serverRsts  = 0
              val quietAfter  = java.lang.System.currentTimeMillis() + 3000L
              client.socket.setSoTimeout(3000)
              var keepReading = true
              while (keepReading)
                try
                  client.readFrame() match {
                    case RstStream(1, code)                      =>
                      serverRsts += 1
                      println(s"[StreamingEdgeSpec] double-cancel-proof serverRst=$code count=$serverRsts")
                      assertTrue(code == H2Error.Code.CANCEL)
                    case Data(1, data, _, _)                     => client.topUp(1, data.length)
                    case _: Data | _: WindowUpdate | _: Settings => ()
                    case _: Ping | _: Headers | _: GoAway        => ()
                    case _: Continuation | _: Priority           => ()
                    case other => throw new AssertionError("Unexpected frame: " + other)
                  }
                catch { case _: SocketTimeoutException => keepReading = false }
              val _           = quietAfter
              println(s"[StreamingEdgeSpec] double-cancel-proof serverRsts=$serverRsts")
              assertTrue(serverRsts == 1)

              val next = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 3)
              assertTrue(next.status == 200)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("GOAWAY mid-stream: in-flight DATA finishes, new streams refused, connection closes after drain") {
        // First 16KB chunk flows immediately (no throttle hit inside it), then
        // 400ms per chunk over 48KB: the 350ms idle timer fires strictly
        // mid-stream (stream ends ~800ms, shutdown follows GOAWAY after the
        // 1000ms drain). The test polls (never sleeps) and acts immediately
        // once GOAWAY lands. 48KB fits the 64KB windows, so no top-up needed.
        val total  = 48 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total, 16384, 400L))),
              )
            },
          ),
        )
        withRawServer(routes, idleTimeout = java.time.Duration.ofMillis(350)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              // First DATA proves streaming started; then stay quiet so the
              // 400ms idle timer fires GOAWAY mid-stream (32KB fits the 64KB
              // windows, so no top-up is needed to keep it flowing).
              var sawFirstData   = false
              var goAway: GoAway = null
              var bytes          = 0L
              client.socket.setSoTimeout(20000)
              val goDeadline     = java.lang.System.currentTimeMillis() + 10000L
              while (goAway == null && java.lang.System.currentTimeMillis() < goDeadline)
                client.readFrame() match {
                  case Headers(1, _, false, _, _, _) => ()
                  case Data(1, data, false, _)       =>
                    bytes += data.length
                    sawFirstData = true
                  case g: GoAway                     => goAway = g
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(sawFirstData)
              println(
                s"[StreamingEdgeSpec] goaway-proof code=${if (goAway == null) "none" else goAway.errorCode} " +
                  s"lastStream=${if (goAway == null) "none" else goAway.lastStreamId}",
              )
              assertTrue(
                goAway != null,
                goAway.errorCode == H2Error.Code.NO_ERROR,
                goAway.lastStreamId == 1,
              )

              // New stream after GOAWAY is refused, not served.
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 3, endStream = true))
              var refused        = false
              val refuseDeadline = java.lang.System.currentTimeMillis() + 10000L
              while (!refused && java.lang.System.currentTimeMillis() < refuseDeadline)
                client.readFrame() match {
                  case RstStream(3, code)                                             =>
                    refused = code == H2Error.Code.REFUSED_STREAM
                    println(s"[StreamingEdgeSpec] goaway-proof refusedRst=$code")
                  case _: Data | _: Headers | _: WindowUpdate | _: Settings | _: Ping => ()
                  case _: RstStream | _: Continuation | _: Priority | _: GoAway       => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(refused)

              // In-flight stream 1 still drains to END_STREAM after GOAWAY.
              // `bytes` already holds the pre-GOAWAY DATA counted above.
              var done  = false
              val drain = java.lang.System.currentTimeMillis() + 20000L
              while (!done && java.lang.System.currentTimeMillis() < drain)
                client.readFrame() match {
                  case Data(1, data, end, _)                      =>
                    bytes += data.length
                    done = end
                  case _: Headers | _: WindowUpdate | _: Settings => ()
                  case _: Ping | _: RstStream | _: Continuation   => ()
                  case _: Priority | _: GoAway                    => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              println(s"[StreamingEdgeSpec] goaway-proof inflightBytes=$bytes")
              assertTrue(done, bytes == total.toLong)

              // After the drain period the server closes TCP.
              client.socket.setSoTimeout(10000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: java.io.IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("GOAWAY then client cancel: single CANCEL echo, no duplicate RST") {
        val total  = 48 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total, 16384, 400L))),
              )
            },
          ),
        )
        withRawServer(routes, idleTimeout = java.time.Duration.ofMillis(350)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              var sawFirstData = false
              var sawGoAway    = false
              client.socket.setSoTimeout(20000)
              val goDeadline   = java.lang.System.currentTimeMillis() + 10000L
              while (!sawGoAway && java.lang.System.currentTimeMillis() < goDeadline)
                client.readFrame() match {
                  case Headers(1, _, false, _, _, _) => ()
                  case Data(1, _, false, _)          => sawFirstData = true
                  case _: GoAway                     => sawGoAway = true
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(sawFirstData)
              assertTrue(sawGoAway)

              // Cancel ordering probe: client RST after server GOAWAY.
              client.sendFrame(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL))
              var cancels        = 0
              val cancelDeadline = java.lang.System.currentTimeMillis() + 10000L
              client.socket.setSoTimeout(2000)
              var draining       = true
              while (draining && java.lang.System.currentTimeMillis() < cancelDeadline)
                try
                  client.readFrame() match {
                    case RstStream(1, code)                           =>
                      cancels += 1
                      println(s"[StreamingEdgeSpec] goaway-cancel-proof echo=$code count=$cancels")
                    case _: Data | _: Headers | _: WindowUpdate       => ()
                    case _: Settings | _: Ping | _: GoAway            => ()
                    case _: RstStream | _: Continuation | _: Priority => ()
                    case other => throw new AssertionError("Unexpected frame: " + other)
                  }
                catch {
                  case _: SocketTimeoutException => draining = false
                  // Drain-close FIN ends the observation: the echo (if any)
                  // precedes it. EOF with cancels == 0 fails below, honestly.
                  case _: java.io.EOFException   => draining = false
                }
              println(s"[StreamingEdgeSpec] goaway-cancel-proof cancels=$cancels")
              assertTrue(cancels == 1)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("over-limit streams are refused with REFUSED_STREAM, connection survives") {
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
        withRawServer(routes, http2Config = Http2Config(maxConcurrentStreams = 2)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              // Occupy both mux slots; response HEADERS prove the handlers run.
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

              // Burst 2x past the limit: both refused, never queued.
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 5, endStream = true))
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 7, endStream = true))
              var refused       = Set.empty[Int]
              val burstDeadline = java.lang.System.currentTimeMillis() + 10000L
              while (refused.size < 2 && java.lang.System.currentTimeMillis() < burstDeadline)
                client.readFrame() match {
                  case RstStream(sid, code) if sid == 5 || sid == 7 =>
                    println(s"[StreamingEdgeSpec] mux-proof refused stream=$sid code=$code")
                    assertTrue(code == H2Error.Code.REFUSED_STREAM)
                    refused += sid
                  case _: Data | _: Headers | _: WindowUpdate       => ()
                  case _: Settings | _: Ping | _: GoAway            => ()
                  case _: RstStream | _: Continuation | _: Priority => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(refused == Set(5, 7))

              // Under-limit streams still finish (16KB each fits the 64KB windows).
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
              println(s"[StreamingEdgeSpec] mux-proof totals=$totals ended=$ended")
              assertTrue(
                ended == Set(1, 3),
                totals.getOrElse(1, -1L) == perStream.toLong,
                totals.getOrElse(3, -1L) == perStream.toLong,
              )

              // Connection survived: a fresh stream round-trips.
              val next = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 9)
              assertTrue(next.status == 200)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("zero-window stall surfaces RST_STREAM instead of parking forever") {
        val total  = 2000
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withRawServer(
          routes,
          http2Config = Http2Config(initialWindowSize = 0),
          sendWindowTimeoutMs = 1500L,
        ) { port =>
          ZIO.attemptBlocking {
            val client    = new RawH2Client(port, autoWindowUpdate = false)
            val startedAt = java.lang.System.currentTimeMillis()
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              var sawHeaders     = false
              var dataFrames     = 0
              var rst: RstStream = null
              val deadline       = java.lang.System.currentTimeMillis() + 15000L
              client.socket.setSoTimeout(15000)
              while (rst == null && java.lang.System.currentTimeMillis() < deadline)
                try
                  client.readFrame() match {
                    case Headers(1, _, false, _, _, _)                          => sawHeaders = true
                    case Data(1, _, _, _)                                       => dataFrames += 1
                    case r: RstStream if r.streamId == 1                        => rst = r
                    case _: WindowUpdate | _: Settings                          => ()
                    case _: Ping                                                => ()
                    case _: Headers | _: GoAway | _: Continuation | _: Priority => ()
                    case _: RstStream | _: Data                                 => ()
                    case other => throw new AssertionError("Unexpected frame: " + other)
                  }
                catch { case _: SocketTimeoutException => () }
              val elapsed        = java.lang.System.currentTimeMillis() - startedAt
              println(
                s"[StreamingEdgeSpec] zero-window-proof sawHeaders=$sawHeaders dataFrames=$dataFrames " +
                  s"rst=${if (rst == null) "none" else rst.errorCode} elapsedMs=$elapsed",
              )
              assertTrue(
                sawHeaders,
                dataFrames == 0,
                rst != null,
                rst.errorCode == H2Error.Code.CANCEL,
                // Bounded: fires near the 1500ms budget, never parks forever.
                elapsed < 15000L,
              )
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("late WINDOW_UPDATE resumes the parked writer with no spurious RST") {
        val total  = 2000
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total))))
            },
          ),
        )
        withRawServer(
          routes,
          http2Config = Http2Config(initialWindowSize = 0),
          sendWindowTimeoutMs = 8000L,
        ) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              var sawHeaders = false
              client.socket.setSoTimeout(20000)
              while (!sawHeaders)
                client.readFrame() match {
                  case Headers(1, _, false, _, _, _) => sawHeaders = true
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(sawHeaders)
              // Parked, not failed: 500ms of silence must produce no RST.
              Thread.sleep(500L)
              client.topUp(1, 65535)
              var bytes      = 0L
              var done       = false
              var sawRst     = false
              val deadline   = java.lang.System.currentTimeMillis() + 20000L
              while (!done && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case Data(1, data, end, _)                                    =>
                    bytes += data.length
                    done = end
                  case RstStream(1, code)                                       =>
                    sawRst = true
                    println(s"[StreamingEdgeSpec] late-wu-proof spuriousRst=$code")
                  case _: Headers | _: WindowUpdate | _: Settings               => ()
                  case _: Ping                                                  => ()
                  case _: RstStream | _: GoAway | _: Continuation | _: Priority => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              // Quiet window after END_STREAM: still no RST for the stream.
              client.socket.setSoTimeout(1500)
              try
                while (true)
                  client.readFrame() match {
                    case RstStream(1, code)                           =>
                      sawRst = true
                      println(s"[StreamingEdgeSpec] late-wu-proof trailingRst=$code")
                    case _: Data | _: Headers | _: WindowUpdate       => ()
                    case _: Settings | _: Ping | _: GoAway            => ()
                    case _: RstStream | _: Continuation | _: Priority => ()
                    case other => throw new AssertionError("Unexpected frame: " + other)
                  }
              catch { case _: SocketTimeoutException => () }
              println(s"[StreamingEdgeSpec] late-wu-proof bytes=$bytes sawRst=$sawRst")
              assertTrue(done, bytes == total.toLong, !sawRst)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("mid-stream WINDOW_UPDATE overflow surfaces RST_STREAM(FLOW_CONTROL_ERROR), connection survives") {
        val total  = 512 * 1024
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(
                Response(status = Status.Ok, body = Body.fromStream(unfoldingBytes(total, 16384, 100L))),
              )
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port, autoWindowUpdate = false)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
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

              // Stream-level overflow (RFC 9113 6.9.1): must reset the stream,
              // never tear the connection down or swallow the violation.
              client.sendFrame(WindowUpdate(streamId = 1, increment = Int.MaxValue))
              var rst: RstStream = null
              val deadline       = java.lang.System.currentTimeMillis() + 10000L
              while (rst == null && java.lang.System.currentTimeMillis() < deadline)
                client.readFrame() match {
                  case r: RstStream if r.streamId == 1                                => rst = r
                  case Data(1, data, _, _)                                            => client.topUp(1, data.length)
                  case _: Data | _: Headers | _: WindowUpdate | _: Settings | _: Ping => ()
                  case _: RstStream | _: GoAway | _: Continuation | _: Priority       => ()
                  case other => throw new AssertionError("Unexpected frame: " + other)
                }
              println(
                s"[StreamingEdgeSpec] overflow-proof rst=${if (rst == null) "none" else rst.errorCode}",
              )
              assertTrue(rst != null, rst.errorCode == H2Error.Code.FLOW_CONTROL_ERROR)

              val next = client.roundTrip("HEAD", "/", Chunk.empty, streamId = 3)
              assertTrue(next.status == 200)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("never-ending periodic stream terminates on client close, writer loop survives") {
        // SSE-shaped: periodic, unbounded, unknown length. The server must not
        // wedge its writer loop on it, and client close must release the
        // stream thread promptly (closeAll → isClosed → abort on next send).
        def infinite(): Stream[Nothing, Byte] =
          Stream.unfold(0) { i =>
            if (i % 1024 == 0) Thread.sleep(70L)
            Some((((i % 251) & 0xff).toByte, i + 1))
          }
        val routes                            = Routes(
          Route(
            RoutePattern.GET,
            handler { (_: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromStream(infinite())))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val before           = countStreamThreads()
            val client           = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              var frames = 0
              client.socket.setSoTimeout(20000)
              while (frames < 3)
                client.readFrame() match {
                  case Headers(1, _, false, _, _, _) => ()
                  case Data(1, data, false, _)       =>
                    client.topUp(1, data.length)
                    frames += 1
                  case _: WindowUpdate | _: Settings => ()
                  case _: Ping                       => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }
              assertTrue(frames == 3)
            } finally client.close()
            val releasedDeadline = java.lang.System.currentTimeMillis() + 15000L
            var after            = countStreamThreads()
            while (after > before && java.lang.System.currentTimeMillis() < releasedDeadline) {
              Thread.sleep(100L)
              after = countStreamThreads()
            }
            println(s"[StreamingEdgeSpec] infinite-proof before=$before after=$after")
            assertTrue(after <= before)

            // Writer loop and listener survive: a fresh connection round-trips.
            val probe = new RawH2Client(port)
            try {
              val next = probe.roundTrip("HEAD", "/", Chunk.empty, streamId = 1)
              assertTrue(next.status == 200)
            } finally probe.close()
          }
        }
      },
      test("bounded consumeSendWindow expires as FlowControlTimeout, windows untouched") {
        ZIO.attemptBlocking {
          val fc       = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 0)
          fc.registerStream(1)
          val started  = java.lang.System.currentTimeMillis()
          var timedOut = false
          try fc.consumeSendWindow(1, 100, 300L)
          catch { case _: FlowController.FlowControlTimeout => timedOut = true }
          val elapsed  = java.lang.System.currentTimeMillis() - started
          println(
            s"[StreamingEdgeSpec] flow-timeout-proof timedOut=$timedOut elapsedMs=$elapsed " +
              s"conn=${fc.connectionWindow} stream=${fc.streamWindow(1)}",
          )
          assertTrue(timedOut, elapsed < 5000L, fc.connectionWindow == 0, fc.streamWindow(1) == 0)
        }
      },
      test("WINDOW_UPDATE before the deadline resumes a parked consume") {
        ZIO.attemptBlocking {
          val fc     = new FlowController(initialConnectionWindow = 0, initialStreamWindow = 1000)
          fc.registerStream(1)
          val waiter = Thread.ofVirtual().start(() => fc.consumeSendWindow(1, 500, 10000L))
          Thread.sleep(300L)
          fc.applyWindowUpdate(0, 500)
          waiter.join(10000L)
          val done   = !waiter.isAlive
          println(
            s"[StreamingEdgeSpec] flow-resume-proof done=$done conn=${fc.connectionWindow} " +
              s"stream=${fc.streamWindow(1)}",
          )
          assertTrue(done, fc.connectionWindow == 0, fc.streamWindow(1) == 500)
        }
      },
    ) @@ sequential

  // ─── helpers ──────────────────────────────────────────────────────────────

  /**
   * Sends HEAD and counts DATA frames for the stream: RFC 9110 section 9.3.2
   * requires zero body frames with END_STREAM already on the response HEADERS.
   */
  private def headAndCountData(client: RawH2Client, streamId: Int): Int = {
    client.sendFrame(client.makeHeaders("HEAD", "/", streamId, endStream = true))
    client.socket.setSoTimeout(20000)
    if (!awaitHeadersEnd(client, streamId)) throw new AssertionError("No end-stream HEADERS for stream " + streamId)
    // Quiet window: any DATA now is a violation (headers already end the stream).
    client.socket.setSoTimeout(500)
    var dataFrames = 0
    try
      while (true)
        client.readFrame() match {
          case Data(sid, _, _, _) if sid == streamId                                           => dataFrames += 1
          case _: WindowUpdate | _: Settings | _: Ping                                         => ()
          case _: Headers | _: GoAway | _: Continuation | _: Priority | _: RstStream | _: Data => ()
          case other => throw new AssertionError("Unexpected frame: " + other)
        }
    catch { case _: SocketTimeoutException => () }
    dataFrames
  }

  private def awaitHeadersEnd(client: RawH2Client, streamId: Int): Boolean = {
    val deadline = java.lang.System.currentTimeMillis() + 20000L
    var result   = false
    var done     = false
    while (!done && java.lang.System.currentTimeMillis() < deadline)
      client.readFrame() match {
        case Headers(sid, _, end, _, _, _) if sid == streamId                                =>
          result = end
          done = true
        case _: WindowUpdate | _: Settings | _: Ping                                         => ()
        case _: Data | _: GoAway | _: Continuation | _: Priority | _: RstStream | _: Headers => ()
        case other => throw new AssertionError("Unexpected frame: " + other)
      }
    result
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
    idleTimeout: java.time.Duration = java.time.Duration.ofSeconds(60),
    sendWindowTimeoutMs: Long = FlowController.DefaultSendWindowTimeoutMs,
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
                  idleTimeout = idleTimeout,
                ),
                DefectHandler.default,
                sendWindowTimeoutMs,
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

    override def close(): Unit = socket.close()

    /**
     * A real HTTP/2 receiver replenishes the sender's windows as it consumes
     * DATA (RFC 9113 6.9): without this, any transfer larger than the 64KB
     * default connection window stalls the server's FlowController forever.
     * Disabled only for tests that assert the stall itself.
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
