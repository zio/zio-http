package zio.http.h2

import java.io.{EOFException, IOException, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

import scala.annotation.experimental
import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.mux.{Mux, MuxError, MuxStream}

import zio.http.h2.hpack.{HeaderField, HpackCodec}

@experimental
final class H2Connection(
  input: InputStream,
  output: OutputStream,
  maxConcurrentStreams: Int = 100,
  flowController: FlowController =
    new FlowController(H2Settings.DefaultInitialWindowSize.toInt, H2Settings.DefaultInitialWindowSize.toInt),
  hpackCodec: HpackCodec = new HpackCodec(),
) {
  import H2Connection._
  import H2Frame._

  private val mux                            = Mux[Int, H2Frame, H2Frame](maxConcurrentStreams)
  private val closed                         = new AtomicBoolean(false)
  private val activeStreams                  = new ConcurrentHashMap[Int, MuxStream[Int, H2Frame, H2Frame]]()
  private val decodedRequestHeaders          = new ConcurrentHashMap[Int, List[HeaderField]]()
  private val writeLock                      = new Object
  private var readBuffer: Chunk[Byte]        = Chunk.empty
  private var peerSettings: List[Setting]    = Nil
  private var pendingHeaders: PendingHeaders = null
  @volatile private var settingsAcknowledged = false
  @volatile private var highestStreamId      = 0
  @volatile private var lastGoAwayStreamId   = Int.MaxValue

  def getWriteLock: Object = writeLock

  /**
   * Returns the request header fields for `streamId`, HPACK-decoded by the
   * single-threaded reader in wire-arrival order.
   *
   * RFC 7541 section 2.3.2 requires header blocks to be decoded in the exact
   * order the peer's encoder produced them, because each block can reference
   * dynamic-table entries established by earlier blocks. Decoding on the
   * per-stream handler threads instead lets blocks be decoded out of wire order
   * (handler threads are scheduled arbitrarily), which desyncs the shared
   * decoder's dynamic table and mis-resolves indexed headers across concurrent
   * streams. Decoding here, on the reader thread, keeps decode order == wire
   * order by construction.
   */
  def takeDecodedRequestHeaders(streamId: Int): List[HeaderField] =
    decodedRequestHeaders.remove(streamId)

  /**
   * Writes a response HEADERS frame directly to the wire, bypassing the
   * per-stream outbound queue and the writer thread.
   *
   * The response HEADERS frame is the only frame type whose bytes depend on the
   * shared per-connection HPACK encoder state (RFC 7541 section 2.3.2 requires
   * the receiver to process header blocks in the exact order the sender's
   * encoder mutated its dynamic table). The writer thread drains
   * `activeStreams` in `ConcurrentHashMap` iteration order, which is unrelated
   * to enqueue order, so routing HEADERS through the queue lets the wire-write
   * order diverge from the encode order and corrupts the peer's dynamic table.
   *
   * `buildFrame` is evaluated inside `writeLock`, so the HPACK encode and the
   * wire write happen atomically on the same thread under the same lock: the
   * wire-write order is the encode order by construction. The same
   * local-frame-write bookkeeping the writer thread would have applied
   * (half-close on END_STREAM, `activeStreams` cleanup) is replicated here so
   * the direct-write path keeps stream lifecycle tracking correct.
   */
  def writeHeadersDirect(stream: MuxStream[Int, H2Frame, H2Frame], buildFrame: => H2Frame.Headers): Unit =
    writeLock.synchronized {
      val frame = buildFrame
      output.write(FrameCodec.encode(frame).toArray)
      output.flush()
      markLocalFrameWrite(stream, frame)
    }

  def run(onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    val writer = Thread.ofVirtual().name("zio-http-h2-writer").start(runnable(writerLoop()))

    try {
      readConnectionPreface()
      writeFrame(Settings(ack = false, H2Settings.DefaultSettings.filterNot(_.id == Setting.ENABLE_PUSH)), flush = true)

      readFrame() match {
        case Settings(false, settings) =>
          peerSettings = settings
          writeFrame(Settings(ack = true, Nil), flush = true)
        case other => throw protocolError("Expected client SETTINGS after preface, received: " + other)
      }

      while (!closed.get()) handleFrame(readFrame(), onStream)
    } catch {
      case _: EOFException => ()
      case _: IOException  => ()
      case NonFatal(error) =>
        shutdown(connectionCancelled("failure: " + error.getMessage))
        throw error
    } finally {
      shutdown(connectionCancelled("closed"))
      writer.interrupt()
      try writer.join()
      catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
      }
    }
  }

  private def handleFrame(frame: H2Frame, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    if (pendingHeaders != null) {
      frame match {
        case continuation: Continuation if continuation.streamId == pendingHeaders.streamId =>
          val next = pendingHeaders.append(continuation)
          if (continuation.endHeaders) {
            pendingHeaders = null
            deliverRequestHeaders(next.toHeaders, onStream)
          } else pendingHeaders = next
        case _                                                                              =>
          throw protocolError("Expected CONTINUATION for stream " + pendingHeaders.streamId + ", received: " + frame)
      }
    } else {
      frame match {
        case headers: Headers if !headers.endHeaders => pendingHeaders = PendingHeaders(headers)
        case headers: Headers                        => deliverRequestHeaders(headers, onStream)
        case _: Continuation => throw protocolError("Unexpected CONTINUATION frame without open header block")
        case other if other.streamId == 0 => handleConnectionFrame(other)
        case other                        => deliverStreamFrame(other, onStream)
      }
    }
  }

  private def handleConnectionFrame(frame: H2Frame): Unit =
    frame match {
      case Settings(false, settings)  =>
        peerSettings = settings
        writeFrame(Settings(ack = true, Nil), flush = true)
      case Settings(true, _)          => settingsAcknowledged = true
      case Ping(false, data)          => writeFrame(Ping(ack = true, data), flush = true)
      case Ping(true, _)              => ()
      case GoAway(lastStreamId, _, _) =>
        lastGoAwayStreamId = lastStreamId
        closed.set(true)
      case wu: WindowUpdate           =>
        applyIncomingWindowUpdate(wu)
      case _                          =>
        throw protocolError("Unexpected connection-level frame: " + frame)
    }

  private def deliverRequestHeaders(headers: Headers, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    // Always decode (advances the shared decoder's table in wire order), but store only the
    // first HEADERS on a stream: a later HEADERS is trailers and must not overwrite the request.
    val isInitialRequestHeaders = isNewClientStream(headers.streamId)
    hpackCodec.decode(headers.headerBlock) match {
      case Right(fields) => if (isInitialRequestHeaders) decodedRequestHeaders.put(headers.streamId, fields)
      case Left(error)   => throw protocolError("Failed to decode HPACK request header block: " + error)
    }
    deliverStreamFrame(headers, onStream)
  }

  private def deliverStreamFrame(frame: H2Frame, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit =
    frame match {
      // RFC 9113 section 5.1 explicitly permits WINDOW_UPDATE/PRIORITY/RST_STREAM on a stream
      // that is already half-closed(remote) or fully closed - including after the stream has
      // been fully removed from the mux (both directions closed, which a fast handler can reach
      // before the connection thread gets around to reading the next frame off the wire).
      // Resolving such a stream via `existingStream` (which throws when the mux has no matching
      // entry) would surface a spurious protocol error here, tearing down the whole connection -
      // including any other stream with a legitimate response still in flight. PRIORITY carries
      // no state at all and is additionally permitted even on a stream id that was never opened
      // ("idle", per RFC 9113 5.1), so it never needs to resolve a stream. WINDOW_UPDATE and
      // RST_STREAM on a stream id that was genuinely never opened remain real protocol
      // violations (see "RST_STREAM for unknown stream causes protocol error"), so only stream
      // ids that were opened at some point (tracked via highestStreamId) take the tolerant path.
      case _: Priority                                    => ()
      case wu: WindowUpdate if isKnownStream(wu.streamId) => applyIncomingWindowUpdate(wu)
      case rst: RstStream if isKnownStream(rst.streamId)  =>
        mux.get(rst.streamId).foreach(_.close())
        activeStreams.remove(rst.streamId)
      case _                                              =>
        val stream = frame match {
          case headers: Headers if isNewClientStream(headers.streamId) => openStream(headers.streamId, onStream)
          case _                                                       => existingStream(frame.streamId)
        }

        frame match {
          case data: Data if data.endStream          =>
            offerInbound(stream, frame)
            stream.signalRemoteClose()
            if (stream.isClosed) activeStreams.remove(frame.streamId)
          case headers: Headers if headers.endStream =>
            offerInbound(stream, frame)
            stream.signalRemoteClose()
            if (stream.isClosed) activeStreams.remove(frame.streamId)
          case _                                     =>
            offerInbound(stream, frame)
        }
    }

  /**
   * True if `streamId` was opened at some point in this connection's lifetime
   * (it may since have fully closed). RFC 9113 5.1 tolerates trailing
   * WINDOW_UPDATE/RST_STREAM for such streams; it does not tolerate them for
   * streams that were never opened at all ("idle").
   */
  private def isKnownStream(streamId: Int): Boolean = streamId <= highestStreamId

  private def applyIncomingWindowUpdate(frame: WindowUpdate): Unit =
    try flowController.applyWindowUpdate(frame.streamId, frame.increment)
    catch {
      case _: NoSuchElementException =>
        () // Stream already fully closed and deregistered from flow control - RFC 9113 5.1 tolerance.
    }

  private def openStream(
    streamId: Int,
    onStream: MuxStream[Int, H2Frame, H2Frame] => Unit,
  ): MuxStream[Int, H2Frame, H2Frame] = {
    if ((streamId & 1) == 0 || streamId <= highestStreamId)
      throw protocolError("Invalid client-initiated stream id: " + streamId)

    val stream = toStream(mux.open(streamId))
    highestStreamId = streamId
    flowController.registerStream(streamId)
    activeStreams.put(streamId, stream)

    Thread
      .ofVirtual()
      .name("zio-http-h2-stream-" + streamId)
      .start(runnable {
        var completed = false
        try {
          onStream(stream)
          completed = true
        } finally {
          if (!completed) {
            if (!stream.isClosed) stream.close()
            activeStreams.remove(streamId)
          }
        }
      })

    stream
  }

  private def existingStream(streamId: Int): MuxStream[Int, H2Frame, H2Frame] =
    mux.get(streamId).getOrElse(throw protocolError("Unknown stream id: " + streamId))

  private def writerLoop(): Unit =
    try {
      while (!closed.get() || !activeStreams.isEmpty) {
        var wrote = false
        val it    = activeStreams.values().iterator()

        while (it.hasNext) {
          val stream = it.next()
          var drain  = true

          while (drain) {
            toOptionalFrame(stream.takeOutbound()) match {
              case Left(_: MuxError)  =>
                activeStreams.remove(stream.id)
                drain = false
              case Right(Some(frame)) =>
                writeFrame(frame, flush = false)
                wrote = true
                markLocalFrameWrite(stream, frame)
              case Right(None)        => drain = false
            }
          }

          if (stream.isClosed) activeStreams.remove(stream.id)
        }

        if (wrote) flushOutput()
        else parkWriter()
      }
    } catch {
      case _: IOException => shutdown(connectionCancelled("writer I/O failure"))
    }

  private def markLocalFrameWrite(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit = {
    frame match {
      case data: Data if data.endStream          => stream.halfClose()
      case headers: Headers if headers.endStream => stream.halfClose()
      case _: RstStream                          => stream.close()
      case _                                     => ()
    }
    if (stream.isClosed) activeStreams.remove(stream.id)
  }

  private def readConnectionPreface(): Unit = {
    val preface = new Array[Byte](ClientPreface.length)
    readFully(preface, 0, preface.length)
    if (!java.util.Arrays.equals(preface, ClientPreface))
      throw protocolError("Invalid HTTP/2 client preface")
  }

  private def readFrame(): H2Frame = {
    while (true) {
      FrameCodec.decode(readBuffer) match {
        case Right((frame, rest))           =>
          readBuffer = rest
          return frame
        case Left(H2Error.InsufficientData) => appendInput()
        case Left(error)                    => throw protocolError("Failed to decode frame: " + error)
      }
    }
    throw protocolError("Unreachable frame read state")
  }

  private def appendInput(): Unit = {
    val bytes = new Array[Byte](8192)
    val read  = input.read(bytes)
    if (read < 0) throw new EOFException("Connection closed while reading HTTP/2 frame")
    readBuffer = readBuffer ++ Chunk.fromArray(java.util.Arrays.copyOf(bytes, read))
  }

  private def readFully(bytes: Array[Byte], offset: Int, length: Int): Unit = {
    var total = 0
    while (total < length) {
      val read = input.read(bytes, offset + total, length - total)
      if (read < 0) throw new EOFException("Connection closed while reading HTTP/2 preface")
      total += read
    }
  }

  private def writeFrame(frame: H2Frame, flush: Boolean): Unit = {
    val bytes = FrameCodec.encode(frame).toArray
    writeLock.synchronized {
      output.write(bytes)
      if (flush) output.flush()
    }
  }

  private def flushOutput(): Unit =
    writeLock.synchronized(output.flush())

  private def offerInbound(stream: MuxStream[Int, H2Frame, H2Frame], frame: H2Frame): Unit =
    toUnit(stream.offerInbound(frame)).foreach(error =>
      throw protocolError("Failed to deliver frame to stream " + stream.id + ": " + error),
    )

  private def isNewClientStream(streamId: Int): Boolean =
    mux.get(streamId).isEmpty && streamId > 0

  private def shutdown(reason: MuxError): Unit =
    if (closed.compareAndSet(false, true)) {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    } else {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    }

  private def parkWriter(): Unit =
    try Thread.sleep(1L)
    catch {
      case _: InterruptedException => Thread.currentThread().interrupt()
    }
}

@experimental
private object H2Connection {
  private val ClientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

  private final case class PendingHeaders(
    streamId: Int,
    headerBlock: Chunk[Byte],
    endStream: Boolean,
    priority: Option[Priority],
    padLength: Int,
  ) {
    def append(frame: H2Frame.Continuation): PendingHeaders =
      copy(headerBlock = headerBlock ++ frame.headerBlock)

    def toHeaders: H2Frame.Headers =
      H2Frame.Headers(streamId, headerBlock, endStream, endHeaders = true, priority, padLength)
  }

  private object PendingHeaders {
    def apply(frame: H2Frame.Headers): PendingHeaders =
      PendingHeaders(frame.streamId, frame.headerBlock, frame.endStream, frame.priority, frame.padLength)
  }

  private def runnable(body: => Unit): Runnable =
    new Runnable {
      def run(): Unit = body
    }

  private def protocolError(message: String): IOException =
    new IOException(message)

  private def toStream(result: Any): MuxStream[Int, H2Frame, H2Frame] =
    result match {
      case stream: MuxStream[_, _, _] => stream.asInstanceOf[MuxStream[Int, H2Frame, H2Frame]]
      case Right(stream)              => stream.asInstanceOf[MuxStream[Int, H2Frame, H2Frame]]
      case Left(error: MuxError)      => throw protocolError("Mux open failed: " + error)
      case error: MuxError            => throw protocolError("Mux open failed: " + error)
      case other                      => throw protocolError("Unexpected mux open result: " + other)
    }

  private def toUnit(result: Any): Option[MuxError] =
    result match {
      case Right(_)              => None
      case Left(error: MuxError) => Some(error)
      case error: MuxError       => Some(error)
      case _                     => None
    }

  private def toOptionalFrame(result: Any): Either[MuxError, Option[H2Frame]] =
    result match {
      case Right(value)          => Right(value.asInstanceOf[Option[H2Frame]])
      case Left(error: MuxError) => Left(error)
      case error: MuxError       => Left(error)
      case value: Option[_]      => Right(value.asInstanceOf[Option[H2Frame]])
      case other                 => Left(MuxError.ProtocolError("Unexpected mux frame result: " + other))
    }

  private def closeQuietly(resource: AutoCloseable): Unit =
    if (resource != null) {
      try resource.close()
      catch {
        case NonFatal(_) => ()
      }
    }

  private def connectionCancelled(reason: String): MuxError =
    MuxError.Cancelled("connection", reason)
}
