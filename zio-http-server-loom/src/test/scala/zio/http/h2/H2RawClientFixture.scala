package zio.http.h2

import java.io.EOFException
import java.net.Socket
import java.nio.charset.StandardCharsets

import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestResult

import zio.http.{BindAddress, BoundAddress, Connector, DefectHandler, Handler, Response, Route, Routes, ServerHandle}
import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, HpackDecoder, HpackEncoder}

/**
 * Shared raw-protocol fixture for behavior-named H2 specs: a real H2Transport
 * bound to an ephemeral port plus a minimal raw HTTP/2 client speaking the wire
 * protocol (preface, SETTINGS, HPACK, multiplexed streams).
 */

object H2RawClientFixture {

  val SimpleRoutes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  def withRawServer[R](
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    withRawServer(SimpleRoutes)(use)

  def withRawServer[R](routes: Routes[Any])(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(
          ServerHandle.live(
            List(
              new H2Transport(routes, Context.empty, Connector(bind = BindAddress.localhost(0)), DefectHandler.default)
                .start(),
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

  def toStreamHelper(result: Any): zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame] =
    result match {
      case s: zio.blocks.mux.MuxStream[?, ?, ?] => s.asInstanceOf[zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame]]
      case Right(s)                             => s.asInstanceOf[zio.blocks.mux.MuxStream[Int, H2Frame, H2Frame]]
      case other                                => throw new AssertionError("Expected MuxStream: " + other)
    }

  final class RawH2Client(val port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    val socket        = new Socket("127.0.0.1", port)
    socket.setSoTimeout(20000)
    private val out   = socket.getOutputStream
    private val rawIn = socket.getInputStream
    private var buf   = Chunk.empty[Byte]

    private val encoder = new HpackEncoder()
    private val decoder = new HpackDecoder()

    /**
     * Buffer for frames received for streams not yet awaited. Maps streamId ->
     * queue of decoded frame events. HEADERS blocks MUST be HPACK-decoded in
     * wire-arrival order (RFC 7541 section 2.3.2), so we decode them eagerly as
     * they come off the wire and buffer the already-decoded header fields
     * rather than the raw block; decoding a buffered block later, out of wire
     * order, would desync this client's single HPACK decoder dynamic table.
     */
    private val frameBuffer: mutable.Map[Int, scala.collection.mutable.Queue[BufferedFrame]] =
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
            if (n < 0) throw new EOFException("Connection closed")
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

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): RawResponse = {
      sendFrame(
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
          endStream = body.isEmpty,
          endHeaders = true,
        ),
      )
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def awaitResponse(streamId: Int): RawResponse = {
      val hdrs   = mutable.ListBuffer.empty[HeaderField]
      var body   = Chunk.empty[Byte]
      var done   = false
      while (!done) {
        val event = frameBuffer.get(streamId) match {
          case Some(queue) if queue.nonEmpty => Some(queue.dequeue())
          case _                             => readAndBufferNextEvent(streamId)
        }

        event.foreach {
          case BufferedHeaders(fields, end) => hdrs ++= fields; done = end
          case BufferedData(data, end)      => body = body ++ data; done = end
        }
      }
      val status = hdrs
        .find(_.name == ":status")
        .map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      RawResponse(status, hdrs.toList, body)
    }

    /**
     * Reads the next frame off the wire, HPACK-decoding HEADERS immediately (in
     * wire-arrival order). If the frame is for `streamId`, returns the decoded
     * event; otherwise buffers it under its stream id and keeps reading.
     */
    private def readAndBufferNextEvent(streamId: Int): Option[BufferedFrame] = {
      while (true) {
        readFrame() match {
          case Settings(false, _)                => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                 => ()
          case _: WindowUpdate                   => ()
          case Headers(sid, block, end, _, _, _) =>
            val fields = decoder.decode(block) match {
              case Right(h) => h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            val event  = BufferedHeaders(fields, end)
            if (sid == streamId) return Some(event)
            else frameBuffer.getOrElseUpdate(sid, scala.collection.mutable.Queue.empty).enqueue(event)
          case Data(sid, data, end, _)           =>
            val event = BufferedData(data, end)
            if (sid == streamId) return Some(event)
            else frameBuffer.getOrElseUpdate(sid, scala.collection.mutable.Queue.empty).enqueue(event)
          case GoAway(_, code, dbg)              =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                                 => ()
        }
      }
      None
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
      readFrame()
    }
  }

  final case class RawResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte]) {
    def bodyText: String = new String(body.toArray, StandardCharsets.UTF_8)
  }

  private sealed trait BufferedFrame
  private final case class BufferedHeaders(fields: List[HeaderField], endStream: Boolean) extends BufferedFrame
  private final case class BufferedData(data: Chunk[Byte], endStream: Boolean)            extends BufferedFrame
}
