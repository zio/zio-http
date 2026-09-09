package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.nio.charset.StandardCharsets

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
import zio.http.{BindAddress, BoundAddress, Connector, Handler, Http2Config, LoomServer, Response, Route, Routes}

/**
 * CONTINUATION flood bound for the H2 server transport.
 *
 * An incomplete header block (HEADERS without END_HEADERS) is buffered pending
 * its CONTINUATION frames. Without a bound a peer can grow that buffer without
 * limit. The buffered encoded bytes are capped at a multiple of the advertised
 * decoded budget (`maxHeaderListSize`); past the cap the stream — and only the
 * stream — is reset with `RST_STREAM(CANCEL)` while the connection stays usable
 * for sibling streams. Trailing in-flight CONTINUATIONs for the reset block are
 * tolerated per RFC 9113 section 5.1.
 */
@experimental
object H2ContinuationFloodSpec extends ZIOSpecDefault {

  /**
   * Socket read timeout: headroom above every explicit await deadline below.
   */
  private val SoTimeoutMs: Int = 20000

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2ContinuationFloodSpec")(
      test("CONTINUATION flood over encoded cap is reset with CANCEL and connection stays usable") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new FloodTestClient(port)
            try {
              // Encoded cap is 4x the default 8192 decoded budget = 32768 bytes.
              val first    = Chunk.fromArray(Array.fill(1024)(0x00.toByte))
              val fragment = Chunk.fromArray(Array.fill(8192)(0x00.toByte))
              client.sendFrame(Headers(streamId = 1, headerBlock = first, endStream = true, endHeaders = false))
              var i        = 0
              var stopped  = false
              while (i < 6 && !stopped) {
                try client.sendFrame(Continuation(streamId = 1, headerBlock = fragment, endHeaders = false))
                catch {
                  // The reset may race the flood: bytes already sent still
                  // prove the flood, and the RST is asserted below (reads are
                  // unaffected). Stops sending; the final fragment below is
                  // skipped the same way.
                  case _: java.net.SocketException => stopped = true
                }
                i += 1
              }
              if (!stopped) {
                // Final fragment (already in flight past the reset) must be tolerated.
                try client.sendFrame(Continuation(streamId = 1, headerBlock = fragment, endHeaders = true))
                catch {
                  case _: java.net.SocketException => ()
                }
              }
              val reset    = client.awaitReset(streamId = 1, timeoutMs = 15000)
              val response = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(reset == H2Error.Code.CANCEL, response.status == 200)
            } finally client.close()
          }
        }
      },
      test("single HEADERS fragment already over a small encoded cap is reset without buffering") {
        withSmallBudgetServer { port =>
          ZIO.attemptBlocking {
            val client = new FloodTestClient(port)
            try {
              // Encoded cap is 4x the 1024 decoded budget = 4096 bytes: one
              // 8KB fragment (under the 16384 frame limit) already exceeds it,
              // so the stream is reset before anything is buffered.
              val fragment = Chunk.fromArray(Array.fill(8192)(0x00.toByte))
              client.sendFrame(Headers(streamId = 1, headerBlock = fragment, endStream = true, endHeaders = false))
              val reset    = client.awaitReset(streamId = 1, timeoutMs = 15000)
              val response = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(reset == H2Error.Code.CANCEL, response.status == 200)
            } finally client.close()
          }
        }
      },
      test("valid headers split across HEADERS and CONTINUATION under the cap succeed") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new FloodTestClient(port)
            try {
              val response = client.roundTripSplitHeaders("GET", "/", streamId = 1)
              assertTrue(response.status == 200)
            } finally client.close()
          }
        }
      },
    ) @@ sequential

  private val SimpleRoutes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  private def withServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(bind = BindAddress.localhost(0))
          new LoomServer(connector).serve(SimpleRoutes, Context.empty)
        },
      )(handle => ZIO.attemptBlocking(handle.shutdownAndWait()).ignore)
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  private def withSmallBudgetServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(
            bind = BindAddress.localhost(0),
            protocol = zio.http.Protocol.H2C(Http2Config(maxHeaderListSize = 1024)),
          )
          new LoomServer(connector).serve(SimpleRoutes, Context.empty)
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

  private final class FloodTestClient(val port: Int) extends AutoCloseable {
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

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse =
      awaitResponseOrReset(
        streamId,
        sendFirst = Some(() => {
          sendFrame(makeHeaders(method, path, streamId, body.isEmpty))
          if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
        }),
      ) match {
        case Left(response) => response
        case Right(code)    => throw new AssertionError("Expected a full response but reset: " + code)
      }

    /**
     * Sends valid request headers split across two fragments (HEADERS without
     * END_HEADERS plus one CONTINUATION): the split is inside one HPACK block,
     * so reassembly must deliver a working request.
     */
    def roundTripSplitHeaders(method: String, path: String, streamId: Int): RawResponse = {
      val block = encoder.encode(
        List(
          HeaderField(":method", method),
          HeaderField(":path", path),
          HeaderField(":scheme", "http"),
          HeaderField(":authority", s"127.0.0.1:$port"),
        ),
      )
      val half  = block.length / 2
      awaitResponseOrReset(
        streamId,
        sendFirst = Some(() => {
          sendFrame(Headers(streamId, block.slice(0, half), endStream = true, endHeaders = false))
          sendFrame(Continuation(streamId, block.slice(half, block.length), endHeaders = true))
        }),
      ) match {
        case Left(response) => response
        case Right(code)    => throw new AssertionError("Expected a full response but reset: " + code)
      }
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
            nextFrameFor(streamId) match {
              case RstStream(_, code) => result = Some(code)
              case _: Headers         => () // Best-effort error response on the dead stream; keep waiting for the RST.
              case _: Data            => ()
              case GoAway(_, code, dbg) =>
                throw new AssertionError(s"GOAWAY while waiting RST: $code ${new String(dbg.toArray)}")
              case _                    => ()
            }
          } catch {
            case _: java.net.SocketTimeoutException => ()
          }
        }
        result.get
      } finally socket.setSoTimeout(SoTimeoutMs)
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

    private def makeHeaders(method: String, path: String, streamId: Int, endStream: Boolean): Headers = {
      val pseudo = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      )
      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(pseudo),
        endStream = endStream,
        endHeaders = true,
      )
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
