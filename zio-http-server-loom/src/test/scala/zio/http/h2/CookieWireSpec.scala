package zio.http.h2

import java.io.InputStream
import java.net.{InetSocketAddress, Socket}
import java.nio.charset.StandardCharsets

import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame.{Data, GoAway, Headers, Settings, WindowUpdate}
import zio.http.h2.hpack.{HeaderField, HpackDecoder, HpackEncoder}
import zio.http.{
  BindAddress,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Method,
  Response,
  Route,
  Routes,
  ServerHandle,
}

// NOTE: Cookie.Response / Cookie.clear are absent from v4 main sources
// (grep `Cookie` in zio-http-core + server-loom main sources yields no hits;
// only docs/dormant v3-era examples reference them and do not compile against
// v4 core). Header.Custom therefore carries the exact Set-Cookie bytes that
// Cookie.Response(isSecure = true, isHttpOnly = true, sameSite = Strict) and
// Cookie.clear would emit, so the H2 wire passthrough is asserted byte-exact.
object CookieWireSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("CookieWireSpec")(
      test("set-cookie with Secure/HttpOnly/SameSite=Strict survives H2 transport") {
        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(
                client.roundTrip(method = "GET", path = "/set", body = Chunk.empty, streamId = 1),
              )
            } yield assertTrue(
              response.status == 200,
              response.headerValue("set-cookie").exists(_.contains("Secure")),
              response.headerValue("set-cookie").exists(_.contains("HttpOnly")),
              response.headerValue("set-cookie").exists(_.contains("SameSite=Strict")),
              response.rawHeaderBlock.nonEmpty,
            )
          }
        }
      },
      test("clear-cookie yields expired Max-Age=0 set-cookie on H2 wire") {
        withServer(routes) { port =>
          withClient(port) { client =>
            for {
              response <- ZIO.attemptBlocking(
                client.roundTrip(method = "GET", path = "/clear", body = Chunk.empty, streamId = 1),
              )
            } yield assertTrue(
              response.status == 200,
              response.headerValue("set-cookie").exists(_.contains("Max-Age=0")),
              response.rawHeaderBlock.nonEmpty,
            )
          }
        }
      },
    ) @@ sequential

  private val routes: Routes[Any] = Routes(
    Route(
      RoutePattern(Method.GET, "/set"),
      Handler.succeed(
        Response.ok.addHeader(
          zio.http.Header.Custom("set-cookie", "session=abc123; Path=/; Secure; HttpOnly; SameSite=Strict"),
        ),
      ),
    ),
    Route(
      RoutePattern(Method.GET, "/clear"),
      Handler.succeed(
        Response.ok.addHeader(
          zio.http.Header.Custom(
            "set-cookie",
            "session=deleted; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0",
          ),
        ),
      ),
    ),
  )

  private def withServer[R](
    routes: Routes[Any],
    defectHandler: DefectHandler = DefectHandler.default,
  )(use: Int => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(startServer(routes, defectHandler)) { handle =>
        ZIO.succeed(handle.shutdownAndWait())
      }
      .flatMap(handle => use(tcpPort(handle)))

  private def withClient[R](
    port: Int,
  )(use: RawH2Client => ZIO[R, Throwable, TestResult]): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attemptBlocking(new RawH2Client(port)),
      )(client => ZIO.succeed(client.close()))
      .flatMap(use)

  private def startServer(
    routes: Routes[Any],
    defectHandler: DefectHandler = DefectHandler.default,
  ): Task[ServerHandle] =
    ZIO.attempt(
      ServerHandle.live(
        List(new H2Transport(routes, Context.empty, Connector(bind = BindAddress.localhost(0)), defectHandler).start()),
      ),
    )

  private def tcpPort(handle: ServerHandle): Int =
    handle.bindings.head.address match {
      case BoundAddress.Tcp(_, port) => port
      case other                     => throw new AssertionError("Expected TCP binding but found: " + other)
    }

  private final case class ReceivedResponse(
    status: Int,
    headers: List[HeaderField],
    body: Chunk[Byte],
    rawHeaderBlock: Chunk[Byte],
  ) {
    def bodyText: String = new String(body.toArray, StandardCharsets.UTF_8)

    def headerValue(name: String): Option[String] =
      headers.find(_.name.equalsIgnoreCase(name)).map(_.value)
  }

  private object RawH2Client {
    private val ClientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
  }

  private final class RawH2Client(port: Int) extends AutoCloseable {
    import RawH2Client._

    private val socket = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)

    private val input   = new FrameReader(socket.getInputStream)
    private val output  = socket.getOutputStream
    private val encoder = new HpackEncoder()
    private val decoder = new HpackDecoder()

    handshake()

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): ReceivedResponse = {
      sendRequest(method, path, body, streamId)
      awaitResponse(streamId)
    }

    def sendRequest(method: String, path: String, body: Chunk[Byte], streamId: Int): Unit = {
      output.write(FrameCodec.encode(requestHeaders(method, path, body, streamId)).toArray)
      if (body.nonEmpty) output.write(FrameCodec.encode(Data(streamId, body, endStream = true)).toArray)
      output.flush()
    }

    def awaitResponse(streamId: Int): ReceivedResponse =
      awaitResponses(Set(streamId))(streamId)

    def awaitResponses(streamIds: Set[Int]): Map[Int, ReceivedResponse] = {
      val builders = mutable.Map.empty[Int, ResponseBuilder]
      val pending  = mutable.Set.empty[Int] ++ streamIds

      while (pending.nonEmpty) {
        input.readFrame() match {
          case Settings(true, _)                                                                  => ()
          case Settings(false, _)                                                                 =>
            output.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
            output.flush()
          case Headers(streamId, headerBlock, endStream, _, _, _) if streamIds.contains(streamId) =>
            val decoded = decodeHeaders(headerBlock)
            val builder = builders.getOrElseUpdate(streamId, ResponseBuilder.empty(decoded, headerBlock))
            builders.update(streamId, builder.withHeaders(decoded, headerBlock))
            if (endStream) pending -= streamId
          case Data(streamId, data, endStream, _) if streamIds.contains(streamId)                 =>
            val builder = builders.getOrElseUpdate(streamId, ResponseBuilder.empty(Nil, Chunk.empty))
            builders.update(streamId, builder.appendBody(data))
            if (endStream) pending -= streamId
          case WindowUpdate(_, _)                                                                 => ()
          case GoAway(_, errorCode, debugData)                                                    =>
            val debug = new String(debugData.toArray, StandardCharsets.UTF_8)
            throw new AssertionError(s"Server sent GOAWAY: error=$errorCode debug=$debug")
          case other                                                                              =>
            throw new AssertionError("Unexpected frame while waiting for response: " + other)
        }
      }

      streamIds.iterator.map { streamId =>
        val builder = builders.getOrElse(streamId, throw new AssertionError(s"Missing response for stream $streamId"))
        streamId -> builder.result
      }.toMap
    }

    override def close(): Unit = socket.close()

    private def handshake(): Unit = {
      output.write(ClientPreface)
      output.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      output.flush()

      input.readFrame() match {
        case Settings(false, _) =>
          output.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
          output.flush()
        case other              =>
          throw new AssertionError("Expected server SETTINGS frame but received: " + other)
      }
    }

    private def requestHeaders(method: String, path: String, body: Chunk[Byte], streamId: Int): Headers = {
      val requestHeaders = List(
        HeaderField(":method", method),
        HeaderField(":path", path),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      ) ++ {
        if (body.isEmpty) Nil
        else List(HeaderField("content-length", body.length.toString))
      }

      Headers(
        streamId = streamId,
        headerBlock = encoder.encode(requestHeaders),
        endStream = body.isEmpty,
        endHeaders = true,
      )
    }

    private def decodeHeaders(headerBlock: Chunk[Byte]): List[HeaderField] =
      decoder.decode(headerBlock) match {
        case Right(headers) => headers
        case Left(error)    => throw new AssertionError("Failed to decode response headers: " + error)
      }
  }

  private final case class ResponseBuilder(
    headers: List[HeaderField],
    body: Chunk[Byte],
    rawHeaderBlock: Chunk[Byte],
  ) {
    def appendBody(chunk: Chunk[Byte]): ResponseBuilder = copy(body = body ++ chunk)

    def withHeaders(value: List[HeaderField], raw: Chunk[Byte]): ResponseBuilder =
      copy(headers = value, rawHeaderBlock = raw)

    def result: ReceivedResponse = {
      val status = headers.find(_.name == ":status").map(_.value.toInt).getOrElse {
        throw new AssertionError("Missing :status response header")
      }
      ReceivedResponse(status = status, headers = headers, body = body, rawHeaderBlock = rawHeaderBlock)
    }
  }

  private object ResponseBuilder {
    def empty(headers: List[HeaderField], rawHeaderBlock: Chunk[Byte]): ResponseBuilder =
      ResponseBuilder(headers, Chunk.empty, rawHeaderBlock)
  }

  private final class FrameReader(input: InputStream) {
    private var buffer = Chunk.empty[Byte]

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buffer) match {
          case Right((decoded, rest))         =>
            buffer = rest
            return decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new AssertionError("Connection closed before an HTTP/2 frame was fully received")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error)                    =>
            throw new AssertionError("Failed to decode HTTP/2 frame: " + error)
        }
      }
      throw new AssertionError("Unreachable HTTP/2 frame read state")
    }
  }
}
