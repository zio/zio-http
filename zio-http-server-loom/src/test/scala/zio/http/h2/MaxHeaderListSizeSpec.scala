package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack, HpackDecoder}
import zio.http.{BindAddress, BoundAddress, Connector, DefectHandler, Handler, Http2Config, Protocol, Response, Route, Routes, ServerHandle}

/**
 * RFC 7540 section 6.5.2: an endpoint that receives a header list larger
 * than it is willing to accept MUST treat it as a connection error of type
 * PROTOCOL_ERROR or a stream error of type ENHANCE_YOUR_CALM. The list must
 * never be silently truncated and must never reach the handler.
 */
@experimental
object MaxHeaderListSizeSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("MaxHeaderListSizeSpec")(
      test("oversized request headers are rejected with RST_STREAM(ENHANCE_YOUR_CALM), never served") {
        val config = Http2Config(maxHeaderListSize = 1024)
        withServer(config) { port =>
          ZIO.attemptBlocking {
            val client = new RejectProbeClient(port)
            try {
              val bigValue = List.fill(11264)("a").mkString
              val block    = Hpack.encode(
                List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                  HeaderField("x-big", bigValue),
                ),
              )
              client.sendFrame(Headers(streamId = 1, headerBlock = block, endStream = true, endHeaders = true))
              val outcome  = client.awaitReject(streamId = 1)
              assertTrue(outcome == Right(H2Error.Code.ENHANCE_YOUR_CALM))
            } finally client.close()
          }
        }
      },
      test("headers within maxHeaderListSize succeed with 200") {
        val config = Http2Config(maxHeaderListSize = 1024)
        withServer(config) { port =>
          ZIO.attemptBlocking {
            val client = new RejectProbeClient(port)
            try {
              val block = Hpack.encode(
                List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                  HeaderField("x-small", "ok"),
                ),
              )
              client.sendFrame(Headers(streamId = 1, headerBlock = block, endStream = true, endHeaders = true))
              val status  = client.awaitStatus(streamId = 1)
              assertTrue(status == 200)
            } finally client.close()
          }
        }
      },
      test("stream WINDOW_UPDATE overflow surfaces RST_STREAM(FLOW_CONTROL_ERROR) and connection stays alive") {
        withServer(Http2Config()) { port =>
          ZIO.attemptBlocking {
            val client = new RejectProbeClient(port)
            try {
              val block = Hpack.encode(
                List(
                  HeaderField(":method", "POST"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                ),
              )
              // endStream=false keeps the handler parked on the body so the
              // stream is still registered in flow control when the update lands.
              client.sendFrame(Headers(streamId = 1, headerBlock = block, endStream = false, endHeaders = true))
              client.sendFrame(WindowUpdate(streamId = 1, increment = Int.MaxValue))
              val outcome  = client.awaitReject(streamId = 1)
              val rejected = outcome == Right(H2Error.Code.FLOW_CONTROL_ERROR)
              // Proof the connection survived: a fresh stream still routes.
              val fresh    = Hpack.encode(
                List(
                  HeaderField(":method", "GET"),
                  HeaderField(":path", "/"),
                  HeaderField(":scheme", "http"),
                  HeaderField(":authority", s"127.0.0.1:$port"),
                ),
              )
              client.sendFrame(Headers(streamId = 3, headerBlock = fresh, endStream = true, endHeaders = true))
              val status   = client.awaitStatus(streamId = 3)
              assertTrue(rejected, status == 200)
            } finally client.close()
          }
        }
      },
      test("Http2Config rejects maxFrameSize=16777216 before bind") {
        ZIO.succeed {
          val message =
            try {
              Http2Config(maxFrameSize = 16777216)
              "<no failure>"
            } catch {
              case error: IllegalArgumentException => error.getMessage
            }
          assertTrue(message == "maxFrameSize must be in [16384,16777215]")
        }
      },
      test("Http2Config accepts boundary maxFrameSize=16777215 and initialWindowSize=2^31-1") {
        ZIO.succeed {
          val config = Http2Config(maxFrameSize = 16777215, initialWindowSize = Int.MaxValue)
          assertTrue(config.maxFrameSize == 16777215, config.initialWindowSize == Int.MaxValue)
        }
      },
      test("Http2Config rejects negative initialWindowSize before bind") {
        ZIO.succeed {
          val message =
            try {
              Http2Config(initialWindowSize = -1)
              "<no failure>"
            } catch {
              case error: IllegalArgumentException => error.getMessage
            }
          // 2^31 needs a Long: Int tops out at 2^31-1, so any Int above the
          // bound is unrepresentable and the runtime guard covers the rest.
          assertTrue(message == "initialWindowSize must be in [0, 2147483647]")
        }
      },
      test("FlowController overflow carries FLOW_CONTROL_ERROR") {
        ZIO.attempt {
          val fc      = new FlowController(65535, 65535)
          fc.registerStream(1)
          val message =
            try {
              fc.applyWindowUpdate(1, Int.MaxValue)
              "<no failure>"
            } catch {
              case error: FlowController.FlowControlException => error.getMessage
            }
          assertTrue(message.contains(H2Error.Code.FLOW_CONTROL_ERROR.value.toString))
        }
      },
    ) @@ sequential

  // ─── helpers ──────────────────────────────────────────────────────────────

  private val SimpleRoutes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  private def withServer[R](
    http2Config: Http2Config,
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(
          ServerHandle.live(
            List(
              new H2Transport(
                SimpleRoutes,
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C(http2Config)),
                DefectHandler.default,
              ).start(),
            ),
          ),
        ),
      )(handle => ZIO.succeed(handle.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, value) => value
          case other                      => throw new AssertionError("Expected TCP binding: " + other)
        }
        use(port)
      }

  /**
   * Raw client that can observe stream rejections: `awaitReject` returns the
   * RST_STREAM error code (or GOAWAY code on connection error) and fails the
   * fiber if the stream is ever served normally — proving no header leak.
   */
  private final class RejectProbeClient(port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    private val socket = new Socket("127.0.0.1", port)
    socket.setSoTimeout(8000)
    private val out    = socket.getOutputStream
    private val rawIn  = socket.getInputStream
    private var buffer = Chunk.empty[Byte]

    private val decoder = new HpackDecoder()

    handshake()

    def sendFrame(frame: H2Frame): Unit = {
      out.write(FrameCodec.encode(frame).toArray)
      out.flush()
    }

    def awaitReject(streamId: Int): Either[H2Error.Code, H2Error.Code] = {
      var done: Either[H2Error.Code, H2Error.Code] = null
      while (done == null) {
        readFrame() match {
          case Settings(false, _)                            => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                             => ()
          case _: WindowUpdate                               => ()
          case RstStream(sid, code) if sid == streamId       => done = Right(code)
          case GoAway(_, code, _)                            => done = Left(code)
          case Headers(sid, _, _, _, _, _) if sid == streamId =>
            throw new AssertionError("Oversized headers were served instead of rejected (header leak)")
          case Data(sid, _, _, _) if sid == streamId         =>
            throw new AssertionError("Oversized headers produced a response body (header leak)")
          case _                                             => ()
        }
      }
      done
    }

    def awaitStatus(streamId: Int): Int = {
      var status: Option[Int] = None
      while (status.isEmpty) {
        readFrame() match {
          case Settings(false, _)                             => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                              => ()
          case _: WindowUpdate                                => ()
          case RstStream(sid, code) if sid == streamId        =>
            throw new AssertionError("Stream was reset unexpectedly: " + code)
          case GoAway(_, code, _)                             =>
            throw new AssertionError("GOAWAY while awaiting response: " + code)
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            decoder.decode(block) match {
              case Right(fields) =>
                status = fields.find(_.name == ":status").map(_.value.toInt)
                if (end && status.isEmpty) throw new AssertionError("Missing :status")
              case Left(error)   => throw new AssertionError("HPACK decode: " + error)
            }
          case _                                              => ()
        }
      }
      status.get
    }

    override def close(): Unit = socket.close()

    private def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buffer) match {
          case Right((frame, rest))           =>
            buffer = rest
            return frame
          case Left(H2Error.InsufficientData) =>
            val tmp  = new Array[Byte](8192)
            val read = rawIn.read(tmp)
            if (read < 0) throw new EOFException("Connection closed")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, read))
          case Left(error)                    =>
            throw new AssertionError("Frame decode error: " + error)
        }
      }
      throw new AssertionError("unreachable")
    }

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
