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
import zio.http._

@experimental
object Http2SettingsWireSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("Http2SettingsWireSpec")(
      test("server advertises custom Http2Config values in its first SETTINGS frame") {
        val config = Http2Config(
          maxConcurrentStreams = 50,
          initialWindowSize = 100000,
          maxFrameSize = 32768,
          maxHeaderListSize = 16384,
        )
        withServer(config) { port =>
          ZIO.attemptBlocking {
            val client = new SettingsCaptureClient(port)
            try {
              val byId = client.serverSettings.map(setting => setting.id -> setting.value).toMap
              assertTrue(
                byId.get(Setting.MAX_CONCURRENT_STREAMS) == Some(50L),
                byId.get(Setting.INITIAL_WINDOW_SIZE) == Some(100000L),
                byId.get(Setting.MAX_FRAME_SIZE) == Some(32768L),
                byId.get(Setting.MAX_HEADER_LIST_SIZE) == Some(16384L),
                byId.get(Setting.HEADER_TABLE_SIZE) == Some(4096L),
                !byId.contains(Setting.ENABLE_PUSH),
              )
            } finally client.close()
          }
        }
      },
      test("default Http2Config advertises RFC defaults with no ENABLE_PUSH") {
        withServer(Http2Config()) { port =>
          ZIO.attemptBlocking {
            val client = new SettingsCaptureClient(port)
            try {
              val byId = client.serverSettings.map(setting => setting.id -> setting.value).toMap
              assertTrue(
                byId.get(Setting.MAX_CONCURRENT_STREAMS) == Some(100L),
                byId.get(Setting.INITIAL_WINDOW_SIZE) == Some(65535L),
                byId.get(Setting.MAX_FRAME_SIZE) == Some(16384L),
                byId.get(Setting.MAX_HEADER_LIST_SIZE) == Some(8192L),
                byId.get(Setting.HEADER_TABLE_SIZE) == Some(4096L),
                !byId.contains(Setting.ENABLE_PUSH),
              )
            } finally client.close()
          }
        }
      },
      test("Http2Config rejects maxFrameSize below the RFC minimum before bind") {
        ZIO.succeed {
          val message =
            try {
              Http2Config(maxFrameSize = 100)
              "<no failure>"
            } catch {
              case error: IllegalArgumentException => error.getMessage
            }
          assertTrue(message == "maxFrameSize must be in [16384,16777215]")
        }
      },
      test("Http2Config rejects maxFrameSize above the RFC maximum before bind") {
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
      test("Http2Config rejects negative initialWindowSize before bind") {
        ZIO.succeed {
          val message =
            try {
              Http2Config(initialWindowSize = -1)
              "<no failure>"
            } catch {
              case error: IllegalArgumentException => error.getMessage
            }
          assertTrue(message == "initialWindowSize must be in [0, 2147483647]")
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
   * Minimal raw client that completes the handshake and captures the server's
   * first (non-ack) SETTINGS frame bytes, decoded via FrameCodec.
   */
  private final class SettingsCaptureClient(port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    private val socket = new Socket("127.0.0.1", port)
    socket.setSoTimeout(10000)
    private val out    = socket.getOutputStream
    private val rawIn  = socket.getInputStream
    private var buffer = Chunk.empty[Byte]

    val serverSettings: List[Setting] = handshake()

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buffer) match {
          case Right((frame, rest))           =>
            buffer = rest
            return frame
          case Left(H2Error.InsufficientData) =>
            val tmp  = new Array[Byte](8192)
            val read = rawIn.read(tmp)
            if (read < 0) throw new EOFException("Connection closed before an HTTP/2 frame was fully received")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, read))
          case Left(error)                    =>
            throw new AssertionError("Failed to decode HTTP/2 frame: " + error)
        }
      }
      throw new AssertionError("Unreachable HTTP/2 frame read state")
    }

    override def close(): Unit = socket.close()

    private def handshake(): List[Setting] = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      val settings = readFrame() match {
        case Settings(false, values) => values
        case other => throw new AssertionError("Expected server SETTINGS frame but received: " + other)
      }
      out.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
      out.flush()
      readFrame() // consume the server's ACK for our SETTINGS
      settings
    }
  }
}
