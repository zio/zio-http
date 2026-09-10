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
 * Request-body bound for the H2 server transport.
 *
 * An unbounded H2 request body lets a malicious client stream gigabytes into
 * `H2Transport.readRequestBody` before any check runs. These tests pin the
 * required behavior against a real loopback `LoomServer`:
 *
 *   - a body at exactly the cap is accepted and echoed,
 *   - a body one byte over the cap is reset mid-stream with
 *     `RST_STREAM(FLOW_CONTROL_ERROR)` while the connection survives for
 *     sibling streams,
 *   - a declared `content-length` larger than the cap is rejected without
 *     reading the body,
 *   - a `content-length` that disagrees with the received bytes is rejected.
 */
@experimental
object H2BodyBoundSpec extends ZIOSpecDefault {

  private val BodyCap: Long = 1024L

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2BodyBoundSpec")(
      test("request body at exactly the cap is accepted and echoed") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new BoundTestClient(port)
            try {
              val body   = Chunk.fromArray(Array.fill(BodyCap.toInt)(0x41.toByte))
              val result = client.post(streamId = 1, body = body, declaredLength = Some(body.length.toString))
              val echoed = result match {
                case PostResult.ResponseReceived(response) => response
                case PostResult.StreamReset(code)          =>
                  throw new AssertionError("at-cap body must be echoed, but the stream was reset: " + code)
              }
              assertTrue(echoed.status == 200, echoed.body == body)
            } finally client.close()
          }
        }
      },
      test("request body one byte over the cap is reset with FLOW_CONTROL_ERROR and the connection survives") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new BoundTestClient(port)
            try {
              // Split across two DATA frames so the running total crosses the
              // cap mid-stream: per-frame accounting must trip on the second.
              val first   = Chunk.fromArray(Array.fill(BodyCap.toInt)(0x41.toByte))
              val second  = Chunk.fromArray(Array(0x42.toByte))
              val code    = client.postSplit(streamId = 1, first = first, second = second, declaredLength = None)
              val sibling = client.roundTrip("GET", "/", Chunk.empty, streamId = 3)
              assertTrue(code == H2Error.Code.FLOW_CONTROL_ERROR, sibling.status == 200)
            } finally client.close()
          }
        }
      },
      test("declared content-length larger than the cap is rejected with FLOW_CONTROL_ERROR") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new BoundTestClient(port)
            try {
              val body = Chunk.fromArray(Array.fill(16)(0x41.toByte))
              val code =
                client.postExpectingReset(streamId = 1, body = body, declaredLength = Some((BodyCap + 1024L).toString))
              assertTrue(code == H2Error.Code.FLOW_CONTROL_ERROR)
            } finally client.close()
          }
        }
      },
      test("content-length disagreeing with received bytes is rejected") {
        withServer { port =>
          ZIO.attemptBlocking {
            val client = new BoundTestClient(port)
            try {
              // Declares 100 bytes but ends the stream after 10: the server
              // must not honor the short body as a complete request.
              val body = Chunk.fromArray(Array.fill(10)(0x41.toByte))
              val code = client.postExpectingReset(streamId = 1, body = body, declaredLength = Some("100"))
              assertTrue(code == H2Error.Code.PROTOCOL_ERROR)
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

  private def withServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt {
          val connector = Connector(
            bind = BindAddress.localhost(0),
            maxRequestBodySize = BodyCap,
          )
          new LoomServer(connector).serve(EchoRoutes, Context.empty)
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

  private final class BoundTestClient(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)
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
     * POST whose body fits in one DATA frame; returns the response or throws if
     * the stream is reset.
     */
    def post(streamId: Int, body: Chunk[Byte], declaredLength: Option[String]): PostResult = {
      val endStream = body.isEmpty
      sendFrame(makeHeaders("POST", "/", streamId, endStream, declaredLength))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponseOrReset(streamId)
    }

    /**
     * POST split across two DATA frames; returns the reset code (throws if a
     * full response arrives).
     */
    def postSplit(
      streamId: Int,
      first: Chunk[Byte],
      second: Chunk[Byte],
      declaredLength: Option[String],
    ): H2Error.Code = {
      sendFrame(makeHeaders("POST", "/", streamId, endStream = false, declaredLength))
      sendFrame(Data(streamId, first, endStream = false))
      sendFrame(Data(streamId, second, endStream = true))
      awaitReset(streamId)
    }

    /**
     * POST a single-DATA-frame body, then wait for the stream reset. Any
     * best-effort error response the handler emits after the reset is ignored:
     * the RST is written directly while the error response is written directly,
     * so either frame may hit the wire first. The pre/post-RST response (if
     * any) is intentionally unspecified here: this spec only pins the reset
     * code.
     */
    def postExpectingReset(streamId: Int, body: Chunk[Byte], declaredLength: Option[String]): H2Error.Code = {
      sendFrame(makeHeaders("POST", "/", streamId, body.isEmpty, declaredLength))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitReset(streamId)
    }

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse = {
      sendFrame(makeHeaders(method, path, streamId, body.isEmpty, None))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponseOrReset(streamId) match {
        case PostResult.ResponseReceived(response) => response
        case PostResult.StreamReset(code)          =>
          throw new AssertionError("Expected a full response but the stream was reset: " + code)
      }
    }

    private def awaitResponseOrReset(streamId: Int): PostResult = {
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
            return PostResult.StreamReset(code)
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
      PostResult.ResponseReceived(RawResponse(status, hdrs.toList, body))
    }

    private def awaitReset(streamId: Int): H2Error.Code = {
      var result: Option[H2Error.Code] = None
      while (result.isEmpty) {
        nextFrameFor(streamId) match {
          case RstStream(_, code)   => result = Some(code)
          case _: Headers           => () // Best-effort error response on the dead stream; keep waiting for the RST.
          case _: Data              => ()
          case GoAway(_, code, dbg) =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                    =>
            throw new AssertionError("Unexpected frame while waiting for RST_STREAM")
        }
      }
      result.get
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

    private def makeHeaders(
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

  private sealed trait PostResult {
    def status: Int
    def body: Chunk[Byte]
    def resetCode: H2Error.Code
  }
  private object PostResult       {
    final case class ResponseReceived(response: RawResponse) extends PostResult {
      def status: Int             = response.status
      def body: Chunk[Byte]       = response.body
      def resetCode: H2Error.Code =
        throw new AssertionError("Expected the stream to be reset but a full response was received: " + status)
    }
    final case class StreamReset(code: H2Error.Code)         extends PostResult {
      def status: Int       = throw new AssertionError("Expected a full response but the stream was reset: " + code)
      def body: Chunk[Byte] = throw new AssertionError("Expected a full response but the stream was reset: " + code)
      def resetCode: H2Error.Code = code
    }
  }
}
