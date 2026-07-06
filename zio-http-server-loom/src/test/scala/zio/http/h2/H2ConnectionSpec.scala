package zio.http.h2

import java.io.{ByteArrayOutputStream, EOFException, IOException, InputStream}
import java.net.{Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{BindAddress, BoundAddress, Connector, DefectHandler, Handler, Response, Route, Routes, ServerHandle}

@experimental
object H2ConnectionSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2ConnectionSpec")(
      test("bad client preface closes connection") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val socket = new Socket("127.0.0.1", port)
            socket.setSoTimeout(3000)
            try {
              socket.getOutputStream.write(Array.fill(24)(0xff.toByte))
              socket.getOutputStream.flush()
              val result =
                try socket.getInputStream.read()
                catch { case _: IOException => -1 }
              assertTrue(result == -1)
            } finally socket.close()
          }
        }
      },
      test("PING ack=false is echoed back as PING ack=true") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val pingData = Chunk.fromArray(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8))
              client.sendFrame(Ping(ack = false, pingData))
              val response = client.readNextMeaningfulFrame()
              response match {
                case Ping(true, data) => assertTrue(data == pingData)
                case _                => assertTrue(false)
              }
            } finally client.close()
          }
        }
      },
      test("PING ack=true is ignored and connection stays alive") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(Ping(ack = true, Chunk.fromArray(Array[Byte](9, 8, 7, 6, 5, 4, 3, 2))))
              client.socket.setSoTimeout(600)
              val noEcho =
                try { client.readFrame(); false }
                catch {
                  case _: SocketTimeoutException => true
                  case _: IOException            => false
                }
              assertTrue(noEcho)
            } finally client.close()
          }
        }
      },
      test("GOAWAY from client closes server connection") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(GoAway(lastStreamId = 0, errorCode = H2Error.Code.NO_ERROR, debugData = Chunk.empty))
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("WindowUpdate streamId=0 is processed and connection stays alive") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendFrame(WindowUpdate(streamId = 0, increment = 1000))
              val pingData = Chunk.fromArray(Array[Byte](1, 1, 1, 1, 1, 1, 1, 1))
              client.sendFrame(Ping(ack = false, pingData))
              val resp     = client.readNextMeaningfulFrame()
              resp match {
                case Ping(true, _) => assertTrue(true)
                case _             => assertTrue(false)
              }
            } finally client.close()
          }
        }
      },
      test("CONTINUATION frames are assembled into complete HEADERS and request succeeds") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              val allHeaders = List(
                HeaderField(":method", "GET"),
                HeaderField(":path", "/"),
                HeaderField(":scheme", "http"),
                HeaderField(":authority", s"127.0.0.1:$port"),
              )
              val encoded    = Hpack.encode(allHeaders)
              val mid        = math.max(1, encoded.length / 2)
              val firstHalf  = encoded.slice(0, mid)
              val secondHalf = encoded.slice(mid, encoded.length)

              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(streamId = 1, headerBlock = firstHalf, endStream = true, endHeaders = false),
                  )
                  .toArray,
              )
              client.sendRaw(
                FrameCodec
                  .encode(
                    Continuation(streamId = 1, headerBlock = secondHalf, endHeaders = true),
                  )
                  .toArray,
              )

              val response = client.awaitResponse(1)
              assertTrue(response.status == 200)
            } finally client.close()
          }
        }
      },
      test("RST_STREAM closes stream but connection stays alive for next stream") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // Open stream 1 without endStream (server waits for body)
              client.sendRaw(
                FrameCodec
                  .encode(
                    client.makeHeaders("POST", "/", streamId = 1, endStream = false),
                  )
                  .toArray,
              )
              // Cancel it immediately
              client.sendRaw(FrameCodec.encode(RstStream(streamId = 1, errorCode = H2Error.Code.CANCEL)).toArray)
              // New request on stream 3 must still succeed
              val response = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(response.status == 200)
            } finally client.close()
          }
        }
      },
      test("unexpected CONTINUATION without pending headers causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    Continuation(
                      streamId = 1,
                      headerBlock = Hpack.encode(List(HeaderField("x-test", "v"))),
                      endHeaders = true,
                    ),
                  )
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("HEADERS(endHeaders=false) followed by non-CONTINUATION causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    Headers(
                      streamId = 1,
                      headerBlock = Hpack.encode(List(HeaderField(":method", "GET"), HeaderField(":path", "/"))),
                      endStream = true,
                      endHeaders = false,
                    ),
                  )
                  .toArray,
              )
              // PING instead of CONTINUATION — protocol violation
              client.sendRaw(FrameCodec.encode(Ping(ack = false, Chunk.fill(8)(0x00.toByte))).toArray)
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("even stream ID causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    client.makeHeaders("GET", "/", streamId = 2, endStream = true),
                  )
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("out-of-order (lower) stream ID causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // First: legitimate stream 3
              client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              // Then: stream 1 which is lower — protocol error
              client.sendRaw(
                FrameCodec
                  .encode(
                    client.makeHeaders("GET", "/", streamId = 1, endStream = true),
                  )
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("RST_STREAM for unknown stream causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              client.sendRaw(
                FrameCodec
                  .encode(
                    RstStream(streamId = 99, errorCode = H2Error.Code.CANCEL),
                  )
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("WindowUpdate overflow on connection window causes protocol error") {
        withRawServer { port =>
          ZIO.attemptBlocking {
            val client = new RawH2Client(port)
            try {
              // connectionWindow starts at 65535; Int.MaxValue increment causes overflow
              client.sendRaw(
                FrameCodec
                  .encode(
                    WindowUpdate(streamId = 0, increment = Int.MaxValue),
                  )
                  .toArray,
              )
              client.socket.setSoTimeout(3000)
              val closed =
                try client.socket.getInputStream.read() == -1
                catch { case _: IOException => true }
              assertTrue(closed)
            } finally {
              try client.close()
              catch { case _: Exception => () }
            }
          }
        }
      },
      test("NonFatal error in H2Connection.run propagates to caller") {
        ZIO.attemptBlocking {
          val preface       = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
          val settingsBytes = FrameCodec.encode(Settings(ack = false, Nil)).toArray
          val handshake     = preface ++ settingsBytes
          var callCount     = 0
          val fakeIn        = new InputStream {
            override def read(): Int                                     = -1
            override def read(buf: Array[Byte], off: Int, len: Int): Int = {
              callCount += 1
              if (callCount == 1) {
                val n = math.min(len, handshake.length)
                java.lang.System.arraycopy(handshake, 0, buf, off, n)
                n
              } else throw new RuntimeException("injected test failure")
            }
          }
          val fakeOut       = new ByteArrayOutputStream()
          val connection    = new H2Connection(fakeIn, fakeOut, maxConcurrentStreams = 10)
          val thrownRef     = new AtomicReference[Throwable](null)
          val thread        = new Thread(() =>
            try connection.run(_ => ())
            catch { case t: Throwable => thrownRef.set(t) },
          )
          thread.start()
          thread.join(5000)
          val thrown        = thrownRef.get()
          assertTrue(
            thrown != null,
            thrown.isInstanceOf[RuntimeException],
            thrown.getMessage == "injected test failure",
          )
        }
      },
    ) @@ sequential

  // ─── helpers ──────────────────────────────────────────────────────────────

  private val SimpleRoutes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  private def withRawServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(
          ServerHandle.live(
            List(
              new H2Transport(
                SimpleRoutes,
                Context.empty,
                Connector(bind = BindAddress.localhost(0)),
                DefectHandler.default,
              ).start(),
            ),
          ),
        ),
      )(h => ZIO.succeed(h.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, p) => p
          case other                  => throw new AssertionError("Expected TCP binding: " + other)
        }
        use(port)
      }

  private final class RawH2Client(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)
    private val out   = socket.getOutputStream
    private val rawIn = socket.getInputStream
    private var buf   = Chunk.empty[Byte]

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

    /**
     * Skip SETTINGS and WindowUpdate frames; return the first substantive
     * frame.
     */
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
            body = body ++ data
            done = end
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
        case Settings(false, _) => sendFrame(Settings(ack = true, Nil))
        case other              => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame() // consume server's ACK for our SETTINGS
    }
  }

  private final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte])
}
