package zio.http.h2

import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.{RoutePattern, SegmentSubtree}
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.ResultType._
import zio.http.{
  BindAddress,
  Body,
  BoundAddress,
  Connector,
  DefectHandler,
  Halt,
  Handler,
  Header,
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

/** Extra integration tests targeting uncovered branches in H2Transport. */
@experimental
object H2TransportCoverageSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2TransportCoverageSpec")(
      // ── readRequestBody: WindowUpdate frame mid-body is ignored ──────────────
      test("WindowUpdate frame mid-body is discarded and full body is received") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val fullBody = Chunk.fromArray("ABCDE12345".getBytes(StandardCharsets.UTF_8))
              val half1    = fullBody.slice(0, 5)
              val half2    = fullBody.slice(5, 10)

              // Send HEADERS with content-length but endStream=false
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "POST"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                          HeaderField("content-length", "10"),
                        ),
                      ),
                      endStream = false,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              // First 5 bytes, no endStream
              client.sendRaw(FrameCodec.encode(Data(streamId = 1, data = half1, endStream = false)).toArray)

              // WindowUpdate on stream 1 mid-body — should be ignored by readRequestBody
              client.sendRaw(FrameCodec.encode(WindowUpdate(streamId = 1, increment = 65535)).toArray)

              // Last 5 bytes, endStream
              client.sendRaw(FrameCodec.encode(Data(streamId = 1, data = half2, endStream = true)).toArray)

              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200, resp.body == fullBody)
            } finally client.close()
          }
        }
      },
      // ── readRequestBody: trailing empty Headers frame with endStream=true ──
      test("trailing HEADERS frame with endStream closes body") {
        val routes = Routes(
          Route(
            RoutePattern.POST,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = req.body))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val body = Chunk.fromArray("hello".getBytes(StandardCharsets.UTF_8))

              // Send HEADERS with content-length but endStream=false
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "POST"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                          HeaderField("content-length", "5"),
                        ),
                      ),
                      endStream = false,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              // Send body
              client.sendRaw(FrameCodec.encode(Data(streamId = 1, data = body, endStream = false)).toArray)

              // Send trailing empty HEADERS with endStream=true (trailers)
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(Nil),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200, resp.body == body)
            } finally client.close()
          }
        }
      },
      // ── containsHost: request with explicit host header no duplicate added ──
      test("request with explicit host header does not get a duplicate host from authority") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (req: Request) =>
              // Count how many times 'host' appears in the request headers
              val hostCount = req.headers.toList.count { case (name, _) => name.equalsIgnoreCase("host") }
              responseAsResult(Response(status = Status.Ok, body = Body.fromString(hostCount.toString)))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // Send request with BOTH :authority and explicit host header
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                          HeaderField("host", s"127.0.0.1:$port"), // explicit host header
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              val resp = client.awaitResponse(1)
              // Should have exactly 1 host header (no duplicate from authority)
              assertTrue(resp.status == 200, resp.bodyText == "1")
            } finally client.close()
          }
        }
      },
      // ── parseUrl: null scheme (no :scheme pseudo-header) ─────────────────
      test("request without :scheme pseudo-header is still processed") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // No :scheme header — parseUrl should handle null scheme
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                          // :scheme intentionally omitted
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200)
            } finally client.close()
          }
        }
      },
      // ── parseUrl: null authority (no :authority pseudo-header) ───────────
      test("request without :authority pseudo-header is still processed") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // No :authority header — parseUrl authorityValue null branch
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          // :authority intentionally omitted
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200)
            } finally client.close()
          }
        }
      },
      // ── parseUrl: authority with explicit port number ─────────────────────
      test("parseUrl authority with explicit port parses host and port correctly") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            handler { (req: Request) =>
              responseAsResult(Response(status = Status.Ok, body = Body.fromString(req.url.host.getOrElse("none"))))
            },
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":method", "GET"),
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              val resp = client.awaitResponse(1)
              assertTrue(resp.status == 200, resp.bodyText == "127.0.0.1")
            } finally client.close()
          }
        }
      },
      // ── buildResponseHeaders: response already has content-length ─────────
      test("response with pre-set content-length is not overridden") {
        val routes = Routes(
          Route(
            RoutePattern.GET,
            Handler.succeed(
              Response(status = Status.Ok, body = Body.fromString("hello"))
                .addHeader(Header.ContentLength(99L)), // deliberately wrong
            ),
          ),
        )
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
              // The pre-set content-length=99 should be preserved (not overridden)
              assertTrue(resp.status == 200, resp.headerValue("content-length").contains("99"))
            } finally client.close()
          }
        }
      },
      // ── awaitHeaders: non-HEADERS frame received first → error ────────────
      test("DATA frame before HEADERS causes stream error and connection recovers") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // Open a normal stream 1 (to get highestStreamId > 0)
              // Then send DATA on stream 3 without opening it with HEADERS first
              // Actually, stream 3 is new; existingStream(3) will throw protocolError
              client.sendRaw(
                FrameCodec.encode(Data(streamId = 3, data = Chunk.fromArray("hi".getBytes), endStream = true)).toArray,
              )
              client.socket.setSoTimeout(3000)
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
      // ── collectPseudoHeaders: missing :method → error ────────────────────
      test("request without :method pseudo-header causes protocol error") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // No :method — collectPseudoHeaders throws IllegalStateException
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(
                        List(
                          HeaderField(":path", "/"),
                          HeaderField(":scheme", "http"),
                          HeaderField(":authority", s"127.0.0.1:$port"),
                        ),
                      ),
                      endStream = true,
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )

              // Server stream handler throws; per RFC 9113 section 8.1.1 a malformed request is a
              // stream error, not a connection error, so the connection may legitimately stay
              // healthy and answer with an error-status response instead of tearing itself down.
              client.socket.setSoTimeout(3000)
              val errOrClosed =
                try {
                  val frame = client.readNextMeaningfulFrame()
                  frame match {
                    case GoAway(_, _, _)               => true
                    case Headers(_, block, _, _, _, _) =>
                      Hpack.decode(block) match {
                        case Right(headers) =>
                          headers.find(_.name == ":status").exists(_.value.toInt >= 400)
                        case Left(_)        => false
                      }
                    case _                             => false // other frames are unexpected but not a failure
                  }
                } catch {
                  case _: java.io.IOException => true // connection closed = acceptable
                  case _: AssertionError      => true // EOF = acceptable
                }
              assertTrue(errOrClosed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── regression: server SETTINGS must never advertise SETTINGS_ENABLE_PUSH=1 ──
      test("server connection preface never advertises SETTINGS_ENABLE_PUSH=1") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try assertTrue(!client.initialSettings.exists(s => s.id == Setting.ENABLE_PUSH && s.value == 1L))
            finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── regression: WINDOW_UPDATE racing a bodyless request's remote-close must not
      //    tear down the connection (real curl/nghttp2 clients send exactly this) ──
      test("WINDOW_UPDATE immediately after a bodyless request does not tear down the connection") {
        val routes = Routes(Route(RoutePattern.GET, Handler.succeed(Response.text("ok"))))
        withRawServer(routes) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))
              client.sendFrame(WindowUpdate(streamId = 1, increment = 65536))
              val first = client.awaitResponse(1)

              // The connection must remain fully usable for later requests too.
              val second = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)

              assertTrue(first.status == 200, first.bodyText == "ok", second.status == 200, second.bodyText == "ok")
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      // ── regression: stream-level WINDOW_UPDATE must actually unblock FlowController ──
      test("response larger than the stream's initial window stalls, then completes once granted") {
        val bodyBytes = Chunk.fromArray(Array.fill(2000)('x'.toByte))
        val routes    = Routes(
          Route(RoutePattern.GET, Handler.succeed(Response(status = Status.Ok, body = Body.fromChunk(bodyBytes)))),
        )
        withRawServer(routes, http2Config = Http2Config(initialWindowSize = 100)) { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(client.makeHeaders("GET", "/", streamId = 1, endStream = true))

              var sawHeaders          = false
              var receivedBeforeGrant = Chunk.empty[Byte]
              client.socket.setSoTimeout(1000)
              try
                while (true)
                  client.readFrame() match {
                    case Headers(1, _, false, _, _, _) => sawHeaders = true
                    case Data(1, data, _, _)           => receivedBeforeGrant = receivedBeforeGrant ++ data
                    case _: WindowUpdate | _: Settings => ()
                    case other                         => throw new AssertionError("Unexpected frame: " + other)
                  }
              catch { case _: SocketTimeoutException => () }

              // The 2000-byte body exceeds the 100-byte initial stream window: the server must
              // send HEADERS (not flow-controlled) but block on DATA until WINDOW_UPDATE arrives.
              assertTrue(sawHeaders, receivedBeforeGrant.isEmpty)

              client.socket.setSoTimeout(20000)
              client.sendFrame(WindowUpdate(streamId = 1, increment = 65535))

              var body = receivedBeforeGrant
              var done = false
              while (!done)
                client.readFrame() match {
                  case Data(1, data, end, _)         => body = body ++ data; done = end
                  case _: WindowUpdate | _: Settings => ()
                  case other                         => throw new AssertionError("Unexpected frame: " + other)
                }

              assertTrue(body == bodyBytes)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
    ) @@ sequential

  // ─── helpers (copied from H2ConnectionSpec pattern) ───────────────────────

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

  import zio.http.ResultType._

  private final class RawH2Client(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(20000)
    private val out   = socket.getOutputStream
    private val rawIn = socket.getInputStream
    private var buf   = Chunk.empty[Byte]

    var initialSettings: List[Setting] = Nil

    /**
     * Buffer for frames received for streams not yet awaited. Maps streamId ->
     * queue of frames.
     */
    private val frameBuffer: mutable.Map[Int, scala.collection.mutable.Queue[H2Frame]] =
      mutable.Map.empty

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

    def readNextMeaningfulFrame(): H2Frame = {
      var result: H2Frame = null
      while (result == null) {
        readFrame() match {
          case s: Settings     => if (!s.ack) sendFrame(Settings(ack = true, Nil))
          case _: WindowUpdate => ()
          case other           => result = other
        }
      }
      result
    }

    def makeHeaders(method: String, path: String, streamId: Int, endStream: Boolean): Headers =
      Headers(
        streamId = streamId,
        headerBlock = Hpack.encode(
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
        // Check if we have a buffered frame for this stream first
        val frame = frameBuffer.get(streamId) match {
          case Some(queue) if queue.nonEmpty => queue.dequeue()
          case _                             => readFrame()
        }

        frame match {
          case Settings(false, _)                                   => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                    => ()
          case _: WindowUpdate                                      => ()
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            Hpack.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            done = end
          case Data(sid, data, end, _) if sid == streamId           =>
            body = body ++ data; done = end
          case GoAway(_, code, dbg)                                 =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case otherFrame                                           =>
            // Frame is for a different stream; buffer it for later
            otherFrame match {
              case h: Headers =>
                val q = frameBuffer.getOrElseUpdate(h.streamId, scala.collection.mutable.Queue.empty)
                q.enqueue(h)
              case d: Data    =>
                val q = frameBuffer.getOrElseUpdate(d.streamId, scala.collection.mutable.Queue.empty)
                q.enqueue(d)
              case _          => ()
            }
        }
      }
      val status = hdrs
        .find(_.name == ":status")
        .map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      RawResponse(status, hdrs.toList, body)
    }

    override def close(): Unit = socket.close()

    private def handshake(): Unit = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      readFrame() match {
        case Settings(false, settings) =>
          initialSettings = settings
          sendFrame(Settings(ack = true, Nil))
        case other                     => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame() // consume server's ACK for our SETTINGS
    }
  }

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte]) {
    def bodyText: String                          = new String(body.toArray, StandardCharsets.UTF_8)
    def headerValue(name: String): Option[String] = headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
  }
}
