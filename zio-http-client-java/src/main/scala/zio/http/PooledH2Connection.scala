package zio.http

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import zio.blocks.chunk.Chunk
import zio.http.h2.FrameCodec
import zio.http.h2.H2Error
import zio.http.h2.H2Frame
import zio.http.h2.H2Frame._
import zio.http.h2.Setting
import zio.http.h2.hpack.HeaderField
import zio.http.h2.hpack.HpackCodec

/**
 * Failure observed before the request HEADERS hit the wire (stale pooled
 * socket, dead peer). The pool may transparently retry these on a fresh
 * connection: the server cannot have acted on a request it never received.
 * Anything thrown after the HEADERS flush propagates - the request may have
 * executed, so a silent pool-level retry would risk duplicating side effects.
 */
private[http] final class StaleConnectionException(message: String, cause: Throwable)
    extends IOException(message, cause) {
  def this(message: String) = this(message, null)
}

/**
 * A single persistent HTTP/2 connection for [[PooledLoomH2Client]].
 *
 * Extends the T14 framing (same prior-knowledge preface, same server-SETTINGS
 * handshake, same header mapping via [[H2WireClient]] - never forked) from
 * one-shot to sequential: the handshake runs once, then each exchange takes the
 * next odd stream id (1, 3, 5, ...) with a connection-scoped [[HpackCodec]] so
 * HPACK dynamic-table state stays consistent across exchanges in both
 * directions.
 *
 * The connection is exclusively leased: at most one exchange is in flight, so
 * the read path needs no lock. All writes funnel through `writeLock` so a
 * concurrent [[H2Exchange.cancel]] (RST_STREAM) cannot interleave a frame.
 * Blocking I/O runs on virtual threads; socket `SoTimeout` bounds every phase
 * and maps to [[TimeoutException]] (never a silent hang).
 *
 * Response bodies are NOT buffered here: [[H2Exchange.bodyInput]] pulls DATA
 * frames on demand (one frame live at a time) with per-chunk connection- and
 * stream-level window top-ups (RFC 9113 section 6.9, T6/T7 backpressure
 * lessons). Request upload stays fully buffered (`Body.toArray`, T14 semantics) -
 * asymmetric by design, documented on the pool.
 */
private[http] final class PooledH2Connection private (
  val socket: Socket,
  private val input: InputStream,
  private val output: OutputStream,
  private val hpack: HpackCodec,
  private var connSendWindow: Int,
  private var streamSendWindow: Int,
  private val maxFrameSize: Int,
) {
  private val reader    = new PooledFrameReader(input)
  private val writeLock = new Object

  @volatile private var dead: Boolean = false

  /**
   * Next client-initiated odd stream id. Guarded by the pool lease (exclusive
   * use).
   */
  private var nextStreamId: Int = 1

  /** Pool-managed recency stamp (nanos); only touched under the pool lock. */
  var lastUsedNanos: Long = java.lang.System.nanoTime()

  def isDead: Boolean   = dead
  def isClosed: Boolean = socket.isClosed || !socket.isConnected

  /**
   * Best-effort socket close without touching `dead` (cancel path owns the
   * verdict).
   */
  private[http] def closeSocket(): Unit = closeQuietly(socket)

  def markDead(): Unit = {
    dead = true
  }

  /**
   * Runs one request/response exchange. Returns after the response HEADERS; the
   * body (unless `endStream`) streams lazily through [[H2Exchange.bodyInput]].
   * Throws [[StaleConnectionException]] when the failure happened before the
   * request HEADERS were flushed (pool may retry on a fresh connection),
   * [[TimeoutException]] on socket-deadline expiry, [[CancellationException]]
   * when [[H2Exchange.cancel]] won the race, and [[IOException]] otherwise
   * (GOAWAY failures name GOAWAY explicitly).
   */
  def exchange(
    request: Request,
    scheme: String,
    authority: String,
    target: String,
    headerTimeoutMs: Int,
    streamTimeoutMs: Option[Long],
    cancelled: AtomicBoolean,
  ): H2Exchange = {
    if (dead || isClosed) throw new StaleConnectionException("Pooled H2 connection is dead or closed")
    if (nextStreamId < 0) {
      dead = true
      throw new StaleConnectionException("Pooled H2 connection exhausted its stream ids")
    }
    val streamId = nextStreamId
    nextStreamId += 2

    // RST idempotence is independent of `cancelled` (the reader-mapping flag
    // is set by the pool before the hook runs - sharing one CAS would skip
    // the RST exactly when cancel wins the race).
    val rstSent                 = new AtomicBoolean(false)
    def failIfCancelled(): Unit =
      if (cancelled.get()) throw new CancellationException("H2 request (stream " + streamId + ") was cancelled")

    socket.setSoTimeout(headerTimeoutMs)
    val body        = request.body.toArray
    val headerBlock = hpack.encode(
      H2WireClient.pseudoHeaders(request, scheme, authority, target) ++ H2WireClient.requestHeaders(request, body),
    )

    var headersFlushed = false
    try {
      writeLock.synchronized {
        failIfCancelled()
        writeFrame(Headers(streamId, headerBlock, endStream = body.isEmpty, endHeaders = true))
        output.flush()
        headersFlushed = true
      }
      sendBody(streamId, body, cancelled)
      val (status, headers, endStream) = readResponseHeaders(streamId, cancelled)
      val contentType                  =
        headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)
      if (endStream) {
        new H2Exchange(
          streamId,
          status,
          headers,
          contentType,
          endStream = true,
          None,
          () => cancelStream(streamId, cancelled, rstSent),
        )
      } else {
        streamTimeoutMs.foreach { ms =>
          socket.setSoTimeout(math.min(math.max(ms, 1L), Int.MaxValue.toLong).toInt)
        }
        val bodyInput = new H2BodyInput(streamId, cancelled, this)
        new H2Exchange(
          streamId,
          status,
          headers,
          contentType,
          endStream = false,
          Some(bodyInput),
          () => cancelStream(streamId, cancelled, rstSent),
        )
      }
    } catch {
      case stale: StaleConnectionException                => throw stale
      case cancel: CancellationException                  => throw cancel
      case timeout: TimeoutException                      =>
        dead = true
        throw timeout
      case socketTimeout: java.net.SocketTimeoutException =>
        dead = true
        if (!headersFlushed)
          throw new StaleConnectionException(
            "Pooled H2 exchange hit a dead socket before request HEADERS were flushed (stream " + streamId + ")",
            socketTimeout,
          )
        throw new TimeoutException("H2 exchange timed out (stream " + streamId + "): " + socketTimeout.getMessage)
      case eof: EOFException                              =>
        // A half-closed stale socket: never observed live, so a pre-flush EOF
        // is retriable; post-flush it poisons the connection.
        dead = true
        if (!headersFlushed)
          throw new StaleConnectionException(
            "Pooled H2 exchange hit EOF before request HEADERS were flushed (stream " + streamId + ")",
            eof,
          )
        throw eof
      case socket: java.net.SocketException               =>
        if (cancelled.get()) failCancelled(streamId)
        dead = true
        if (!headersFlushed)
          throw new StaleConnectionException(
            "Pooled H2 exchange hit a dead socket before request HEADERS were flushed (stream " + streamId + ")",
            socket,
          )
        throw socket
      case failure: Throwable                             =>
        if (!headersFlushed)
          throw new StaleConnectionException(
            "Pooled H2 exchange failed before request HEADERS were flushed (stream " + streamId + ")",
            failure,
          )
        failure match {
          case io: IOException => throw io
          case _               => throw new IOException("Pooled H2 exchange failed (stream " + streamId + ")", failure)
        }
    }
  }

  private def cancelStream(streamId: Int, cancelled: AtomicBoolean, rstSent: AtomicBoolean): Unit = {
    if (rstSent.compareAndSet(false, true)) {
      cancelled.set(true)
      writeLock.synchronized {
        try {
          writeFrame(RstStream(streamId, H2Error.Code.CANCEL))
          output.flush()
        } catch { case _: Throwable => () }
      }
      dead = true
      closeQuietly(socket)
    }
  }

  private def failCancelled(streamId: Int): Nothing =
    throw new CancellationException("H2 request (stream " + streamId + ") was cancelled")

  private def sendBody(streamId: Int, body: Array[Byte], cancelled: AtomicBoolean): Unit = {
    var offset = 0
    while (offset < body.length) {
      if (cancelled.get()) failCancelled(streamId)
      while (connSendWindow <= 0 || streamSendWindow <= 0) {
        if (cancelled.get()) failCancelled(streamId)
        awaitSendWindow(streamId, cancelled)
      }
      val length = math.min(body.length - offset, math.min(maxFrameSize, math.min(connSendWindow, streamSendWindow)))
      val chunk  = Chunk.fromArray(java.util.Arrays.copyOfRange(body, offset, offset + length))
      writeLock.synchronized {
        if (cancelled.get()) failCancelled(streamId)
        writeFrame(Data(streamId, chunk, endStream = offset + length == body.length))
        output.flush()
      }
      connSendWindow -= length
      streamSendWindow -= length
      offset += length
    }
  }

  /**
   * Parks the sending (virtual) thread until the peer tops up the connection-
   * and stream-level send windows (RFC 9113 section 6.9). Bounded on every axis
   * (MINOR-3), so a slow receiver never parks an unbounded number of threads:
   *   - thread count: the connection is exclusively leased (one exchange in
   *     flight), and connections are capped by the pool semaphores
   *     (`PoolConfig.maxTotal` globally, `maxPerHost` per authority) - at most
   *     `maxTotal` senders can park here at once (100 by default);
   *   - memory: a parked sender stages at most one `maxFrameSize` chunk (16 KiB
   *     by default) plus the T14-buffered request body documented on the pool -
   *     never an open-ended buffer per thread;
   *   - time: every `readFrame` runs under socket `SoTimeout` (request/stream
   *     deadline via `DeadlineConfig`), so expiry throws [[TimeoutException]]
   *     and evicts the connection - never a silent hang.
   *
   * Peer-side contract: the server parks at most `maxConcurrentStreams` streams
   * (the `Mux` bound, `H2Connection` default 100) with 16 KiB staging each and
   * a 30s cap (`FlowController.DefaultSendWindowTimeoutMs`) - the "thousands of
   * threads x 16 KiB" scenario is bounded-by-config on both ends of the wire.
   */
  private def awaitSendWindow(streamId: Int, cancelled: AtomicBoolean): Unit =
    reader.readFrame() match {
      case WindowUpdate(0, increment)          =>
        connSendWindow += increment
      case WindowUpdate(`streamId`, increment) =>
        streamSendWindow += increment
      case Ping(false, data)                   =>
        writeLock.synchronized {
          writeFrame(Ping(ack = true, data))
          output.flush()
        }
      case Settings(false, _)                  =>
        writeLock.synchronized {
          writeFrame(Settings(ack = true, Nil))
          output.flush()
        }
      case RstStream(`streamId`, code)         =>
        throw new IOException("HTTP/2 stream reset while sending request body: " + code)
      case GoAway(_, code, _)                  =>
        dead = true
        throw new IOException("HTTP/2 connection closed (GOAWAY) while sending request body: " + code)
      case _                                   => ()
    }

  private def readResponseHeaders(streamId: Int, cancelled: AtomicBoolean): (Status, zio.http.Headers, Boolean) = {
    val headerBytes = new ByteArrayOutputStream()
    var endStream   = false
    var headersDone = false
    while (!headersDone) {
      if (cancelled.get()) failCancelled(streamId)
      try {
        reader.readFrame() match {
          case Headers(`streamId`, block, streamEnd, endHeaders, _, _) =>
            appendBounded(headerBytes, block)
            endStream = streamEnd
            if (endHeaders) headersDone = true
            else readContinuations(streamId, headerBytes, cancelled)
          case Ping(false, data)                                       =>
            writeLock.synchronized {
              writeFrame(Ping(ack = true, data))
              output.flush()
            }
          case Settings(false, _)                                      =>
            writeLock.synchronized {
              writeFrame(Settings(ack = true, Nil))
              output.flush()
            }
          case RstStream(`streamId`, code)                             =>
            throw new IOException("HTTP/2 stream reset while awaiting response headers: " + code)
          case GoAway(_, code, _)                                      =>
            dead = true
            throw new IOException("HTTP/2 connection closed (GOAWAY) while awaiting response headers: " + code)
          case _                                                       => ()
        }
      } catch {
        case timeout: java.net.SocketTimeoutException =>
          throw new TimeoutException(
            "H2 response headers timed out (stream " + streamId + "): " + timeout.getMessage,
          )
        case eof: EOFException                        =>
          dead = true
          throw eof
        case socket: java.net.SocketException         =>
          if (cancelled.get()) failCancelled(streamId)
          dead = true
          throw socket
      }
    }

    val fields = hpack.decode(Chunk.fromArray(headerBytes.toByteArray)) match {
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
    (status, builder.build(), endStream)
  }

  private def readContinuations(streamId: Int, headerBytes: ByteArrayOutputStream, cancelled: AtomicBoolean): Unit = {
    var done = false
    while (!done) {
      if (cancelled.get()) failCancelled(streamId)
      reader.readFrame() match {
        case Continuation(`streamId`, block, endHeaders) =>
          appendBounded(headerBytes, block)
          if (endHeaders) done = true
        case Ping(false, data)                           =>
          writeLock.synchronized {
            writeFrame(Ping(ack = true, data))
            output.flush()
          }
        case Settings(false, _)                          =>
          writeLock.synchronized {
            writeFrame(Settings(ack = true, Nil))
            output.flush()
          }
        case RstStream(`streamId`, code)                 =>
          throw new IOException("HTTP/2 stream reset inside CONTINUATION sequence: " + code)
        case GoAway(_, code, _)                          =>
          dead = true
          throw new IOException("HTTP/2 connection closed (GOAWAY) inside CONTINUATION sequence: " + code)
        case other                                       =>
          throw new IOException("Expected HTTP/2 CONTINUATION but received: " + other)
      }
    }
  }

  /**
   * Body pull loop shared by [[H2BodyInput]]; see its contract for windowing.
   */
  private[http] def readBodyFrame(body: H2BodyInput): Boolean = {
    val streamId  = body.streamId
    val cancelled = body.cancelled
    var filled    = body.carryLength > 0
    var done      = false
    while (!filled && !done) {
      if (cancelled.get()) failCancelled(streamId)
      try {
        reader.readFrame() match {
          case Data(`streamId`, data, streamEnd, _)                    =>
            val bytes = data.toArray
            body.append(bytes)
            writeLock.synchronized {
              writeFrame(WindowUpdate(0, bytes.length))
              writeFrame(WindowUpdate(streamId, bytes.length))
              output.flush()
            }
            if (streamEnd) done = true
            filled = true
          case Headers(`streamId`, block, streamEnd, endHeaders, _, _) =>
            // Trailers: drain any CONTINUATION tail, then the stream is over iff
            // END_STREAM is set. Trailer values are intentionally dropped - the
            // status/headers were already delivered and blocks Headers has no
            // trailer channel.
            if (!endHeaders) {
              val discard = new ByteArrayOutputStream()
              readContinuations(streamId, discard, cancelled)
            } else if (block.nonEmpty) {
              appendBounded(new ByteArrayOutputStream(), block)
            }
            if (streamEnd) done = true
          // Otherwise the HEADERS carried no payload: loop for the next frame.
          case Ping(false, data)                                       =>
            writeLock.synchronized {
              writeFrame(Ping(ack = true, data))
              output.flush()
            }
          case Settings(false, _)                                      =>
            writeLock.synchronized {
              writeFrame(Settings(ack = true, Nil))
              output.flush()
            }
          case RstStream(`streamId`, code)                             =>
            throw new IOException("HTTP/2 stream reset while reading response body: " + code)
          case GoAway(_, code, _)                                      =>
            dead = true
            throw new IOException("HTTP/2 connection closed (GOAWAY) while reading response body: " + code)
          case _                                                       => ()
        }
      } catch {
        case timeout: java.net.SocketTimeoutException =>
          dead = true
          throw new TimeoutException(
            "H2 response body timed out (stream " + streamId + "): " + timeout.getMessage,
          )
        case eof: EOFException                        =>
          dead = true
          throw eof
        case socket: java.net.SocketException         =>
          if (cancelled.get()) failCancelled(streamId)
          dead = true
          throw socket
      }
    }
    done
  }

  private def appendBounded(into: ByteArrayOutputStream, block: Chunk[Byte]): Unit = {
    if (into.size() + block.length > PooledH2Connection.MaxHeaderBytes)
      throw new IOException("HTTP/2 response header block exceeds 64 KiB cap")
    into.write(block.toArray, 0, block.length)
  }

  private def writeFrame(frame: H2Frame): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    output.write(bytes, 0, bytes.length)
  }

  def close(): Unit = {
    dead = true
    closeQuietly(socket)
  }

  private def closeQuietly(socket: Socket): Unit =
    if (socket != null) {
      try socket.close()
      catch { case _: Throwable => () }
    }
}

/**
 * Response HEADERS for one exchange plus the hooks to stream/cancel the body.
 * `bodyInput` is None when `endStream` was set on the response HEADERS.
 */
private[http] final class H2Exchange(
  val streamId: Int,
  val status: Status,
  val headers: zio.http.Headers,
  val contentType: ContentType,
  val endStream: Boolean,
  val bodyInput: Option[H2BodyInput],
  val cancel: () => Unit,
)

/**
 * Blocking [[InputStream]] over one H2 response stream. Pulls DATA frames on
 * demand into a reused 16 KiB carry buffer (one frame live at a time -
 * heap-bounded no matter the body size) and tops up connection- and
 * stream-level windows per chunk (RFC 9113 section 6.9).
 *
 * `close()` is a deliberate no-op towards the socket: the blocks-stream wrapper
 * closes this stream at EOF, but the pooled connection must survive for reuse.
 * Terminal state is observable via [[completedCleanly]] (pool reuses only clean
 * connections; anything else is evicted, never black-holed).
 */
private[http] final class H2BodyInput(
  val streamId: Int,
  val cancelled: AtomicBoolean,
  private val conn: PooledH2Connection,
) extends InputStream {
  private var carry             = new Array[Byte](16384)
  private var carryPos          = 0
  private[http] var carryLength = 0
  private var eof               = false
  private var failed: Throwable = null

  /** True once END_STREAM was seen and every byte was handed out. */
  def completedCleanly: Boolean = eof && carryPos >= carryLength && failed == null

  private[http] def append(bytes: Array[Byte]): Unit = {
    if (bytes.length > carry.length - carryLength) {
      val grown = new Array[Byte](carryLength + bytes.length)
      java.lang.System.arraycopy(carry, carryPos, grown, 0, carryLength - carryPos)
      carryPos = 0
      carry = grown
    } else if (carryPos > 0 && bytes.length > carry.length - (carryPos + carryLength)) {
      java.lang.System.arraycopy(carry, carryPos, carry, 0, carryLength - carryPos)
      carryLength = carryLength - carryPos
      carryPos = 0
    }
    java.lang.System.arraycopy(bytes, 0, carry, carryPos + carryLength, bytes.length)
    carryLength += bytes.length
  }

  override def read(): Int = {
    val one = new Array[Byte](1)
    val got = this.read(one, 0, 1)
    if (got < 0) -1 else one(0) & 0xff
  }

  override def read(buffer: Array[Byte], offset: Int, length: Int): Int = {
    if (length == 0) return 0
    if (cancelled.get())
      throw new CancellationException("H2 response body (stream " + streamId + ") was cancelled")
    if (failed != null) throw failed
    if (eof && carryPos >= carryLength) return -1
    try {
      if (carryPos >= carryLength) {
        carryPos = 0
        carryLength = 0
        val streamEnded = conn.readBodyFrame(this)
        if (streamEnded) eof = true
        if (carryPos >= carryLength) {
          if (eof) return -1
          return read(buffer, offset, length)
        }
      }
      val available = carryLength - carryPos
      val count     = math.min(available, length)
      java.lang.System.arraycopy(carry, carryPos, buffer, offset, count)
      carryPos += count
      count
    } catch {
      case failure: Throwable =>
        if (failed == null) failed = failure
        throw failure
    }
  }

  /**
   * No-op towards the socket by design (see class doc). Records EOF so later
   * reads return -1 instead of pulling.
   */
  override def close(): Unit = ()
}

private[http] object PooledH2Connection {
  private val Preface: Array[Byte]   =
    "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)
  private val DefaultMaxFrame: Int   = 16384
  private val DefaultSendWindow: Int = 65535
  private val ConnectionStream: Int  = 0
  private val MaxHeaderBytes: Int    = 65536

  /**
   * Handshake over already-connected streams: client preface + empty SETTINGS,
   * then first server SETTINGS (acking PINGs / topping the window / acking
   * server SETTINGS along the way, exactly like [[H2WireClient]]).
   */
  def handshake(socket: Socket, input: InputStream, output: OutputStream): PooledH2Connection = {
    output.write(Preface)
    writeFrame(output, Settings(ack = false, Nil))
    output.flush()
    val reader       = new PooledFrameReader(input)
    var maxFrame     = DefaultMaxFrame
    var connWindow   = DefaultSendWindow
    var streamWindow = DefaultSendWindow
    var done         = false
    while (!done) {
      reader.readFrame() match {
        case Settings(false, settings)                 =>
          settings.foreach {
            case Setting(id, value) if id == Setting.MAX_FRAME_SIZE      =>
              maxFrame = math.min(math.max(value.toInt, 16384), 16777215)
            case Setting(id, value) if id == Setting.INITIAL_WINDOW_SIZE =>
              streamWindow = value.toInt
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
          connWindow += increment
        case _                                         => ()
      }
    }
    new PooledH2Connection(socket, input, output, new HpackCodec(), connWindow, streamWindow, maxFrame)
  }

  private def writeFrame(output: OutputStream, frame: H2Frame): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    output.write(bytes, 0, bytes.length)
  }
}

private[http] final class PooledFrameReader(input: InputStream) {
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
