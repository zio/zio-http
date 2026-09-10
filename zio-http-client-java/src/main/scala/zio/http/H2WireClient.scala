package zio.http

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.http.h2.FrameCodec
import zio.http.h2.H2Error
import zio.http.h2.H2Frame
import zio.http.h2.H2Frame._
import zio.http.h2.Setting
import zio.http.h2.hpack.HeaderField
import zio.http.h2.hpack.Hpack

/**
 * Single-request HTTP/2 wire exchange over an already-connected byte stream.
 *
 * Speaks client prior-knowledge (`PRI * HTTP/2.0` preface + empty SETTINGS, RFC
 * 9113 section 3.4) on cleartext sockets and the exact same frame sequence on
 * TLS sockets that already negotiated `h2` via ALPN. Frame encoding/decoding
 * and HPACK are reused from h2-codec (`FrameCodec`, `Hpack`) - never
 * reimplemented here.
 *
 * Scope (T14): one request per connection, bodies fully buffered
 * (`Body.toArray`, like `JavaH2Client`). Connection pooling, multiplexed
 * streams, retries, and streaming upload belong to T15: the seams are the fixed
 * stream id (`StreamId`), the single `execute` call per socket in
 * [[LoomH2ClientDriver]], and [[PoolConfig]] (read by the driver where
 * applicable, pooling itself deferred).
 */
@experimental
private[http] object H2WireClient {

  private val Preface: Array[Byte]   = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
  private val StreamId: Int          = 1
  private val DefaultMaxFrame: Int   = 16384
  private val DefaultSendWindow: Int = 65535
  private val ConnectionStream: Int  = 0
  private val MaxHeaderBytes: Int    = 65536

  /**
   * Default response-body cap, sourced from [[ClientConfig]] so every client
   * leg enforces the same bound. Callers with a config pass
   * `config.maxResponseBodySize` explicitly via [[execute]].
   */
  private val MaxBodyBytes: Long             = ClientConfig.DefaultMaxResponseBodySize
  private val ConnectionHeaders: Set[String] =
    Set("connection", "keep-alive", "proxy-connection", "transfer-encoding", "upgrade")

  /**
   * Runs one request/response exchange. `scheme`/`authority`/`target` feed the
   * `:scheme` / `:authority` / `:path` pseudo-headers; everything else comes
   * from `request`. Throws [[IOException]] (including
   * [[java.net.SocketTimeoutException]] when the socket read deadline fires) on
   * transport or protocol failure.
   */
  def execute(
    input: InputStream,
    output: OutputStream,
    request: Request,
    scheme: String,
    authority: String,
    target: String,
    maxBodyBytes: Long = MaxBodyBytes,
  ): Response = {
    val reader = new FrameReader(input)
    output.write(Preface)
    writeFrame(output, Settings(ack = false, Nil))
    output.flush()

    val negotiated = awaitServerSettings(reader, output)

    val body        = request.body.toArray
    val headerBlock = Hpack.encode(pseudoHeaders(request, scheme, authority, target) ++ requestHeaders(request, body))
    writeFrame(output, Headers(StreamId, headerBlock, endStream = body.isEmpty, endHeaders = true))
    output.flush()
    sendBody(output, reader, body, negotiated)

    readResponse(reader, output, maxBodyBytes)
  }

  private final class Negotiated(var maxFrameSize: Int, var sendWindow: Int)

  private def awaitServerSettings(reader: FrameReader, output: OutputStream): Negotiated = {
    val negotiated = new Negotiated(DefaultMaxFrame, DefaultSendWindow)
    var done       = false
    while (!done) {
      reader.readFrame() match {
        case Settings(false, settings)                 =>
          settings.foreach {
            case Setting(id, value) if id == Setting.MAX_FRAME_SIZE      =>
              negotiated.maxFrameSize = math.min(math.max(value.toInt, 16384), 16777215)
            case Setting(id, value) if id == Setting.INITIAL_WINDOW_SIZE =>
              negotiated.sendWindow = value.toInt
            case _                                                       => ()
          }
          writeFrame(output, Settings(ack = true, Nil))
          output.flush()
          done = true
        case Settings(true, _)                         => ()
        case Ping(false, data)                         =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case WindowUpdate(ConnectionStream, increment) =>
          negotiated.sendWindow += increment
        case _                                         => ()
      }
    }
    negotiated
  }

  private[http] def pseudoHeaders(
    request: Request,
    scheme: String,
    authority: String,
    target: String,
  ): List[HeaderField] =
    List(
      HeaderField(":method", request.method.name),
      HeaderField(":path", target),
      HeaderField(":scheme", scheme),
      HeaderField(":authority", authority),
    )

  private[http] def requestHeaders(request: Request, body: Array[Byte]): List[HeaderField] = {
    val builder = List.newBuilder[HeaderField]
    request.headers.toList.foreach { case (name, value) =>
      val lower = name.toLowerCase(java.util.Locale.ROOT)
      if (!lower.startsWith(":") && lower != "host" && lower != "content-length" && !ConnectionHeaders.contains(lower))
        builder += HeaderField(lower, value)
    }
    if (body.nonEmpty) builder += HeaderField("content-length", body.length.toString)
    builder.result()
  }

  private def sendBody(output: OutputStream, reader: FrameReader, body: Array[Byte], negotiated: Negotiated): Unit = {
    var offset = 0
    while (offset < body.length) {
      while (negotiated.sendWindow <= 0) awaitSendWindow(reader, output, negotiated)
      val length = math.min(body.length - offset, math.min(negotiated.maxFrameSize, negotiated.sendWindow))
      val chunk  = Chunk.fromArray(java.util.Arrays.copyOfRange(body, offset, offset + length))
      writeFrame(output, Data(StreamId, chunk, endStream = offset + length == body.length))
      output.flush()
      negotiated.sendWindow -= length
      offset += length
    }
  }

  // Send-window park: bounded like [[PooledH2Connection]] (one-shot leg, so at
  // most the calling thread parks; each read runs under socket SoTimeout and
  // stages at most one maxFrameSize chunk) - see that wait for the full bound
  // math (MINOR-3).
  private def awaitSendWindow(reader: FrameReader, output: OutputStream, negotiated: Negotiated): Unit =
    reader.readFrame() match {
      case WindowUpdate(ConnectionStream, increment) =>
        negotiated.sendWindow += increment
      case WindowUpdate(StreamId, increment)         =>
        negotiated.sendWindow += increment
      case Ping(false, data)                         =>
        writeFrame(output, Ping(ack = true, data))
        output.flush()
      case Settings(false, _)                        =>
        writeFrame(output, Settings(ack = true, Nil))
        output.flush()
      case RstStream(StreamId, code)                 =>
        throw new IOException("HTTP/2 stream reset while sending request body: " + code)
      case GoAway(_, code, _)                        =>
        throw new IOException("HTTP/2 connection closed (GOAWAY) while sending request body: " + code)
      case _                                         => ()
    }

  private def readResponse(reader: FrameReader, output: OutputStream, maxBodyBytes: Long): Response = {
    val headerBytes = new ByteArrayOutputStream()
    var endStream   = false
    var headersDone = false
    while (!headersDone) {
      reader.readFrame() match {
        case Headers(StreamId, block, streamEnd, endHeaders, _, _) =>
          appendBounded(headerBytes, block)
          endStream = streamEnd
          if (endHeaders) headersDone = true
          else readContinuations(reader, output, headerBytes)
        case Ping(false, data)                                     =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case Settings(false, _)                                    =>
          writeFrame(output, Settings(ack = true, Nil))
          output.flush()
        case RstStream(StreamId, code)                             =>
          throw new IOException("HTTP/2 stream reset while awaiting response headers: " + code)
        case GoAway(_, code, _)                                    =>
          throw new IOException("HTTP/2 connection closed (GOAWAY) while awaiting response headers: " + code)
        case _                                                     => ()
      }
    }

    val fields = Hpack.decode(Chunk.fromArray(headerBytes.toByteArray)) match {
      case Right(decoded) => decoded
      case Left(error)    => throw new IOException("Failed to HPACK-decode response headers: " + error)
    }
    val status = fields.collectFirst { case HeaderField(":status", value, _) => value } match {
      case Some(value) =>
        try Status.fromInt(value.toInt)
        catch {
          case _: NumberFormatException => throw new IOException("Invalid HTTP/2 :status pseudo-header: " + value)
        }
      case None        => throw new IOException("HTTP/2 response headers carry no :status pseudo-header")
    }

    val builder = HeadersBuilder.make()
    fields.foreach {
      case HeaderField(name, value, _) if !name.startsWith(":") => builder.add(name, value)
      case _                                                    => ()
    }
    val headers = builder.build()

    val bodyBytes   =
      if (endStream) Array.emptyByteArray
      else readBody(reader, output, maxBodyBytes)
    val contentType =
      headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)

    Response(
      status = status,
      headers = headers,
      body = Body.fromArray(bodyBytes, contentType),
      version = Version.`HTTP/2.0`,
    )
  }

  private def readContinuations(reader: FrameReader, output: OutputStream, headerBytes: ByteArrayOutputStream): Unit = {
    var done = false
    while (!done) {
      reader.readFrame() match {
        case Continuation(StreamId, block, endHeaders) =>
          appendBounded(headerBytes, block)
          if (endHeaders) done = true
        case Ping(false, data)                         =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case Settings(false, _)                        =>
          writeFrame(output, Settings(ack = true, Nil))
          output.flush()
        case RstStream(StreamId, code)                 =>
          throw new IOException("HTTP/2 stream reset inside CONTINUATION sequence: " + code)
        case GoAway(_, code, _)                        =>
          throw new IOException("HTTP/2 connection closed (GOAWAY) inside CONTINUATION sequence: " + code)
        case other                                     =>
          throw new IOException("Expected HTTP/2 CONTINUATION but received: " + other)
      }
    }
  }

  private def readBody(reader: FrameReader, output: OutputStream, maxBodyBytes: Long): Array[Byte] = {
    val collected = new ByteArrayOutputStream()
    var total     = 0L
    var done      = false
    while (!done) {
      reader.readFrame() match {
        case Data(StreamId, data, streamEnd, _)       =>
          val len   = data.length
          if (total + len > maxBodyBytes) throw ResponseBodyTooLarge(maxBodyBytes)
          val bytes = data.toArray
          collected.write(bytes, 0, bytes.length)
          total += bytes.length
          // Replenish connection- and stream-level flow-control windows as we
          // consume, so multi-DATA-frame responses never stall.
          writeFrame(output, WindowUpdate(ConnectionStream, bytes.length))
          writeFrame(output, WindowUpdate(StreamId, bytes.length))
          output.flush()
          if (streamEnd) done = true
        case Headers(StreamId, _, streamEnd, _, _, _) =>
          if (streamEnd) done = true
        case Ping(false, data)                        =>
          writeFrame(output, Ping(ack = true, data))
          output.flush()
        case Settings(false, _)                       =>
          writeFrame(output, Settings(ack = true, Nil))
          output.flush()
        case RstStream(StreamId, code)                =>
          throw new IOException("HTTP/2 stream reset while reading response body: " + code)
        case GoAway(_, code, _)                       =>
          throw new IOException("HTTP/2 connection closed (GOAWAY) while reading response body: " + code)
        case _                                        => ()
      }
    }
    collected.toByteArray
  }

  private def appendBounded(into: ByteArrayOutputStream, block: Chunk[Byte]): Unit = {
    if (into.size() + block.length > MaxHeaderBytes)
      throw new IOException("HTTP/2 response header block exceeds 64 KiB cap")
    into.write(block.toArray, 0, block.length)
  }

  private def writeFrame(output: OutputStream, frame: H2Frame): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    output.write(bytes, 0, bytes.length)
  }

  private final class FrameReader(input: InputStream) {
    private var buffer = Chunk.empty[Byte]

    def readFrame(): H2Frame = {
      var result: H2Frame = null
      while (result == null) {
        FrameCodec.decode(buffer) match {
          case Right((decoded, rest))         =>
            buffer = rest
            result = decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new EOFException("HTTP/2 connection closed mid-frame")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error)                    =>
            throw new IOException("Failed to decode HTTP/2 frame: " + error)
        }
      }
      result
    }
  }
}
