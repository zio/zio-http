package zio.http.h2

import java.io.{EOFException, IOException, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import scala.annotation.experimental
import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.mux.{Mux, MuxError, MuxStream}

import zio.http.Http2Config
import zio.http.h2.hpack.{HeaderField, HpackCodec}

@experimental
final class H2Connection(
  input: InputStream,
  output: OutputStream,
  maxConcurrentStreams: Int = 100,
  flowController: FlowController =
    new FlowController(H2Settings.DefaultInitialWindowSize.toInt, H2Settings.DefaultInitialWindowSize.toInt),
  hpackCodec: HpackCodec = new HpackCodec(),
  localSettings: Option[List[Setting]] = None,
  maxHeaderListSize: Int = 8192,
  idleTimeoutMs: Long = 60000,
  requestTimeoutMs: Long = 30000,
  drainTimeoutMs: Long = 1000,
  // Header-fragment (CONTINUATION) completion deadline for an incomplete
  // header block. Expiry resets the stream with RST_STREAM(CANCEL).
  // Non-positive disables. Forwarded from Connector.headerTimeoutMs.
  // Appended last (never inserted mid-list) so existing positional call
  // sites cannot silently rebind.
  headerTimeoutMs: Long = 5000,
) {
  import H2Connection._
  import H2Frame._

  private val mux                                   = Mux[Int, H2Frame, H2Frame](maxConcurrentStreams)
  private val effectiveLocalSettings: List[Setting] = localSettings.getOrElse(
    H2Connection.settingsFor(
      maxConcurrentStreams,
      H2Settings.DefaultInitialWindowSize.toInt,
      H2Settings.DefaultMaxFrameSize.toInt,
      None,
    ),
  )
  private val closed                                = new AtomicBoolean(false)
  private val activeStreams                         = new ConcurrentHashMap[Int, MuxStream[Int, H2Frame, H2Frame]]()
  private val activeHandlers                        = ConcurrentHashMap.newKeySet[Thread]()
  private val decodedRequestHeaders                 = new ConcurrentHashMap[Int, List[HeaderField]]()
  private val writeLock                             = new Object
  private var readBuffer: Chunk[Byte]               = Chunk.empty
  private var peerSettings: List[Setting]           = Nil
  private var pendingHeaders: PendingHeaders        = null
  @volatile private var pendingTimer: ScheduledFuture[?] = null
  // Stream whose partial header block was discarded by the flood cap or the
  // header-timeout expiry (RST CANCEL already sent): trailing in-flight
  // CONTINUATIONs for it are ignored per RFC 9113 section 5.1 tolerance so
  // the connection survives. Cleared when a new header block starts. 0 = none
  // (0 is never a valid stream id).
  @volatile private var resetPendingStreamId: Int        = 0
  @volatile private var settingsAcknowledged             = false
  @volatile private var highestStreamId                  = 0
  @volatile private var lastGoAwayStreamId               = Int.MaxValue

  /**
   * Live control plane for this connection (T5): shares this connection's
   * `writeLock` (one writer lock per OutputStream), the mux, and reports the
   * real highest stream id on idle GOAWAY. All of its blocking waits run on
   * Loom virtual threads, never ZIO fibers.
   */
  private val control: H2ConnectionControl =
    new H2ConnectionControl(
      output,
      mux,
      idleTimeoutMs = idleTimeoutMs,
      requestTimeoutMs = requestTimeoutMs,
      writeLock = writeLock,
      lastStreamId = () => highestStreamId,
      drainTimeoutMs = drainTimeoutMs,
      onGracefulShutdown = () => initiateGracefulShutdown(),
    )

  def getWriteLock: Object = writeLock

  def connectionControl: H2ConnectionControl = control

  def currentHighestStreamId: Int = highestStreamId

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
    control.startIdleTimer()

    try {
      readConnectionPreface()
      writeFrame(Settings(ack = false, effectiveLocalSettings.filterNot(_.id == Setting.ENABLE_PUSH)), flush = true)

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
      cancelPendingDeadline()
      control.stopIdleTimer()
      shutdown(connectionCancelled("closed"))
      writer.interrupt()
      try writer.join()
      catch {
        case _: InterruptedException => Thread.currentThread().interrupt()
      }
    }
  }

  private def handleFrame(frame: H2Frame, onStream: MuxStream[Int, H2Frame, H2Frame] => Unit): Unit = {
    // Any inbound frame is connection activity: keep the idle timer honest.
    control.resetIdleTimer()
    // Snapshot once: the header-timeout thread nulls pendingHeaders on
    // expiry, so triple-reading the volatile across the match below is a
    // TOCTOU race (NPE/stale-stream mismatch). All branches use the local.
    val pending = pendingHeaders
    if (pending != null) {
      frame match {
        case continuation: Continuation if continuation.streamId == pending.streamId =>
          pending.append(continuation, pendingHeaderCapBytes) match {
            case None       =>
              // Encoded-fragment flood: reset only this stream with CANCEL and
              // discard the partial block so the connection stays usable for
              // sibling streams (mirrors the header-timeout expiry cleanup).
              discardPendingBlock(continuation.streamId)
            case Some(next) =>
              if (continuation.endHeaders) {
                pendingHeaders = null
                cancelPendingDeadline()
                deliverRequestHeaders(next.toHeaders, onStream)
              } else pendingHeaders = next
          }
        case _                                                                       =>
          throw protocolError("Expected CONTINUATION for stream " + pending.streamId + ", received: " + frame)
      }
    } else {
      frame match {
        case headers: Headers if !headers.endHeaders =>
          if (headers.headerBlock.length.toLong > pendingHeaderCapBytes) {
            // A single fragment already over the encoded cap: reset without
            // buffering anything, connection stays usable.
            discardPendingBlock(headers.streamId)
          } else {
            pendingHeaders = PendingHeaders(headers)
            resetPendingStreamId = 0
            startPendingDeadline(headers.streamId)
          }
        case headers: Headers                        => deliverRequestHeaders(headers, onStream)
        case c: Continuation if c.streamId == resetPendingStreamId && resetPendingStreamId != 0 =>
          () // Trailing CONTINUATION for a flood/timed-out header block already reset; ignore per 5.1 tolerance.
        case c: Continuation if isKnownStream(c.streamId) =>
          () // Trailing CONTINUATION after a timed-out header block was reset; ignore per 5.1 tolerance.
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
      case Right(fields) =>
        if (isInitialRequestHeaders) {
          // RFC 7540 6.5.2: a header list larger than maxHeaderListSize is a
          // stream error of type ENHANCE_YOUR_CALM. Reject before the headers
          // reach any handler: never truncate, never leak.
          if (H2Connection.headerListSize(fields) > maxHeaderListSize.toLong) {
            rejectHeaders(headers.streamId, H2Error.Code.ENHANCE_YOUR_CALM)
            return
          }
          decodedRequestHeaders.put(headers.streamId, fields)
        }
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
      case headers: Headers if isNewClientStream(headers.streamId) && control.isGoingAway =>
        // RFC 9113 6.8: after we sent GOAWAY, refuse new streams with
        // REFUSED_STREAM so the client can retry elsewhere. The id is
        // consumed so a later reuse still trips the monotonicity check.
        refuseStream(headers.streamId)
      case _                                                                              =>
        val stream = frame match {
          case headers: Headers if isNewClientStream(headers.streamId) =>
            // Over-limit opens never queue: the mux bound (maxConcurrentStreams)
            // is enforced at open by refusing with REFUSED_STREAM (RFC 9113 6.8
            // — retryable elsewhere), reusing the GOAWAY refusal path. The id
            // is consumed, so a later reuse trips the monotonicity check
            // instead of opening fresh. A refusal carries no stream to deliver
            // to, so `deliverToStream` is skipped via the Option.
            openStream(headers.streamId, onStream) match {
              case Some(opened) => opened
              case None         => return
            }
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
      case _: FlowController.FlowControlException if frame.streamId != 0 =>
        // Stream-level overflow is a stream error of type FLOW_CONTROL_ERROR
        // (RFC 9113 6.9.1): reset the stream, keep the connection alive.
        // Connection-level (streamId 0) overflow still propagates as a
        // connection error and tears the connection down.
        sendReset(frame.streamId, H2Error.Code.FLOW_CONTROL_ERROR)
    }

  /**
   * Sends RST_STREAM via the single T5 send site
   * ([[H2ConnectionControl.sendRstStream]], which shares this connection's
   * `writeLock` by construction — see the `control` wiring above — so frame
   * bytes and mux cancellation keep the exact same lock, order, and error code
   * as a direct write). `sendRstStream` tolerates absent mux entries
   * (pre-stream refusals/rejections), so the only local work left is forgetting
   * the stream maps.
   */
  private def sendReset(streamId: Int, errorCode: H2Error.Code): Unit = {
    try control.sendRstStream(streamId, errorCode)
    catch {
      case _: NoSuchElementException => ()
    }
    activeStreams.remove(streamId)
    decodedRequestHeaders.remove(streamId)
  }

  /**
   * Rejects an over-limit header block before any stream exists for it: no mux
   * entry to cancel, but the id is consumed so a later reuse trips the
   * monotonic stream-id check in openStream instead of opening fresh.
   */
  private def rejectHeaders(streamId: Int, errorCode: H2Error.Code): Unit = {
    sendReset(streamId, errorCode)
    if (streamId > highestStreamId) highestStreamId = streamId
  }

  /**
   * Discards an incomplete header block and resets its stream with CANCEL: the
   * single cleanup for the flood-cap and header-timeout paths. The RST goes
   * through the single shared send site (`H2ConnectionControl.sendRstStream`,
   * which shares this connection's `writeLock`), so the connection stays usable
   * for sibling streams. The id is consumed (like `rejectHeaders`) so a later
   * reuse trips the monotonic stream-id check, and trailing in-flight
   * CONTINUATIONs for it are ignored via `resetPendingStreamId` (RFC 9113
   * section 5.1 tolerance).
   */
  private def discardPendingBlock(streamId: Int): Unit = {
    cancelPendingDeadline()
    // The RST goes through the single shared send site
    // (`H2ConnectionControl.sendRstStream`, which shares this connection's
    // `writeLock`), so the connection stays usable for sibling streams. Best
    // effort: if the peer already went away the send failure is swallowed.
    // Sent only by the first claimer (see takePendingDiscard): exactly one
    // RST goes out per discarded block.
    if (takePendingDiscard(streamId, freshOk = true)) {
      try control.sendRstStream(streamId, H2Error.Code.CANCEL)
      catch {
        case NonFatal(_) => ()
      }
    }
  }

  /**
   * Header-fragment (slow-loris) deadline for an incomplete header block.
   * Reuses the shared `H2ConnectionControl.scheduleTimeoutRst` mechanism
   * (virtual-thread sleep, never a ZIO fiber; single shared wire-write lock).
   * Pre-open blocks have no mux entry yet, so `requireOpenStream = false` and
   * expiry predicates purely on the block still being pending. On expiry the
   * stream is reset with CANCEL and the partial block discarded so the
   * connection stays usable for sibling streams.
   */
  private def startPendingDeadline(streamId: Int): Unit = {
    cancelPendingDeadline()
    if (headerTimeoutMs <= 0L) return
    pendingTimer = control.scheduleTimeoutRst(
      streamId,
      headerTimeoutMs,
      H2Error.Code.CANCEL,
      () => takePendingDiscard(streamId, freshOk = false),
      requireOpenStream = false,
    )
  }

  /**
   * Claims the discard of the incomplete header block for `streamId` and
   * reports whether this claimer owes the RST: records the discard (nulls the
   * block, consumes the id like `rejectHeaders` so a later reuse trips the
   * monotonic stream-id check, arms trailing-CONTINUATION tolerance via
   * `resetPendingStreamId` per RFC 9113 section 5.1) once, exactly-once across
   * the reader-side flood discard and the timer-thread expiry. The first claim
   * wins — a buffered block, or (reader only, `freshOk`) a fresh single
   * fragment already over the cap that was never buffered; the loser observes
   * the recorded id and stands down, so exactly one RST goes out per discarded
   * block. The timer never claims fresh (`freshOk = false`): a
   * normally-completed block records no id, and must not draw a spurious RST. A
   * pending block for another stream is never claimed (unreachable by protocol:
   * no other frame may intervene before the block completes).
   *
   * Best-effort like the upstream request-timer cancel race: a CONTINUATION
   * completing the block in the same instant as the expiry may still lose to a
   * concurrently-claiming timer.
   */
  private def takePendingDiscard(streamId: Int, freshOk: Boolean): Boolean = {
    val pending = pendingHeaders
    if (pending != null && pending.streamId != streamId) return false
    if (pending == null && (!freshOk || resetPendingStreamId == streamId)) return false
    pendingHeaders = null
    resetPendingStreamId = streamId
    activeStreams.remove(streamId)
    if (streamId > highestStreamId) highestStreamId = streamId
    true
  }

  private def cancelPendingDeadline(): Unit = {
    val timer = pendingTimer
    if (timer != null) {
      pendingTimer = null
      timer.cancel(true)
    }
  }

  /**
   * Encoded-byte budget for one buffered (incomplete) header block: a multiple
   * of the advertised decoded budget. HPACK encoding can expand relative to
   * decoded size, so the wire cap is a multiple rather than the decoded limit
   * itself; past it the peer is flooding and the stream is reset with CANCEL.
   */
  @inline private def pendingHeaderCapBytes: Long = maxHeaderListSize.toLong * 4L

  /**
   * Refuses a new stream opened after our GOAWAY (RFC 9113 6.8): the client
   * must treat it as never processed and may retry on a new connection.
   */
  private def refuseStream(streamId: Int): Unit = {
    sendReset(streamId, H2Error.Code.REFUSED_STREAM)
    if (streamId > highestStreamId) highestStreamId = streamId
  }

  /**
   * RFC 9113 6.8 graceful shutdown: runs on the control's idle-timer virtual
   * thread after GOAWAY was sent. In-flight streams keep draining through the
   * writer loop during the drain period, then the connection closes.
   *
   * The sleep is a deadline loop, not a single interruptible sleep:
   * `resetIdleTimer` interrupts this thread on every inbound frame, including
   * frames that arrive during the drain itself (a post-GOAWAY HEADERS refused
   * with REFUSED_STREAM, a late RST). A single sleep would collapse the drain
   * on the first such frame and close TCP under in-flight streams — and under
   * the very RST the refusal path just wrote. Interrupts are absorbed until the
   * full drain period elapses, and the interrupt status is restored on exit so
   * structured shutdown upstream still observes it.
   *
   * The loop exits early once the connection is fully drained — `activeStreams`
   * empty and no per-stream handler thread still running. Both halves matter: a
   * client RST removes the stream entry eagerly while its handler thread may
   * still be mid-response (its abort RST echo is written from that thread), so
   * exiting on an empty stream map alone would close TCP under the echo. Sleeps
   * run in 1ms quanta (matching the writer park) so a drain that completes
   * mid-period is observed promptly.
   */
  private[h2] def initiateGracefulShutdown(): Unit = {
    val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(drainTimeoutMs.max(0L))
    var remaining     = deadlineNanos - System.nanoTime()
    var interrupted   = false
    while (remaining > 0L && (!activeStreams.isEmpty || !activeHandlers.isEmpty)) {
      try {
        TimeUnit.NANOSECONDS.sleep(math.min(remaining, 1000000L))
      } catch {
        case _: InterruptedException => interrupted = true
      }
      remaining = deadlineNanos - System.nanoTime()
    }
    if (interrupted) Thread.currentThread().interrupt()
    shutdown(connectionCancelled("idle timeout"))
  }

  private def openStream(
    streamId: Int,
    onStream: MuxStream[Int, H2Frame, H2Frame] => Unit,
  ): Option[MuxStream[Int, H2Frame, H2Frame]] = {
    if ((streamId & 1) == 0 || streamId <= highestStreamId)
      throw protocolError("Invalid client-initiated stream id: " + streamId)

    // Widened to MuxOpenResult (see alias): matching Left(_) covers 2.13,
    // the bare error covers the 3.x union; both keep the exact same refusal
    // semantics.
    val openedResult: MuxOpenResult = mux.open(streamId)
    openedResult match {
      case Left(_: MuxError.CapacityExceeded) | _: MuxError.CapacityExceeded =>
        // Bound proved: the mux never queues past maxConcurrentStreams — the
        // open is refused on the wire (retryable) instead of buffering
        // unboundedly or tearing the connection down.
        refuseStream(streamId)
        None
      case opened                                                            =>
        val stream = toStream(opened)
        highestStreamId = streamId
        flowController.registerStream(streamId)
        activeStreams.put(streamId, stream)

        // Registered before start so the GOAWAY drain never observes a fully
        // idle connection while a handler is still spawning: an entry present
        // without a live thread only ever extends the drain to its deadline.
        val handler = Thread
          .ofVirtual()
          .name("zio-http-h2-stream-" + streamId)
          .unstarted(runnable {
            var completed = false
            try {
              onStream(stream)
              completed = true
            } finally {
              if (!completed) {
                if (!stream.isClosed) stream.close()
                activeStreams.remove(streamId)
              }
              activeHandlers.remove(Thread.currentThread())
            }
          })
        activeHandlers.add(handler)
        try handler.start()
        catch {
          case NonFatal(error) =>
            activeHandlers.remove(handler)
            throw error
        }

        Some(stream)
    }
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

  private def shutdown(reason: MuxError): Unit = {
    control.stopIdleTimer()
    if (closed.compareAndSet(false, true)) {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    } else {
      mux.closeAll(reason)
      closeQuietly(input)
      closeQuietly(output)
    }
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

  /**
   * Mux `open` result across toolchains: the Scala 3 Mux returns a
   * `MuxStream | MuxError` union while the 2.13 Mux boxes the same result into
   * an `Either` (see `toStream`, which shims both shapes the same way). Named
   * so the open site does not widen to a bare `Any`.
   */
  private type MuxOpenResult = Any

  /**
   * Builds the SETTINGS payload to advertise from a single [[Http2Config]]
   * source: 0x3 MAX_CONCURRENT_STREAMS, 0x4 INITIAL_WINDOW_SIZE, 0x5
   * MAX_FRAME_SIZE, 0x6 MAX_HEADER_LIST_SIZE, plus 0x1 HEADER_TABLE_SIZE 4096.
   * ENABLE_PUSH is never advertised (the send site filters it out as before).
   */
  def settingsFor(config: Http2Config): List[Setting] =
    settingsFor(
      config.maxConcurrentStreams,
      config.initialWindowSize,
      config.maxFrameSize,
      Some(config.maxHeaderListSize),
    )

  /**
   * Decoded header-list size per RFC 7540 6.5.2: the sum, over every entry, of
   * name length plus value length plus 32 octets of framing overhead.
   */
  def headerListSize(fields: List[HeaderField]): Long =
    fields.foldLeft(0L) { (total, field) =>
      total +
        field.name.getBytes(StandardCharsets.UTF_8).length +
        field.value.getBytes(StandardCharsets.UTF_8).length + 32L
    }

  def settingsFor(
    maxConcurrentStreams: Int,
    initialWindowSize: Int,
    maxFrameSize: Int,
    maxHeaderListSize: Option[Int],
  ): List[Setting] = {
    if (maxFrameSize < H2Settings.MinimumMaxFrameSize || maxFrameSize > H2Settings.MaximumMaxFrameSize)
      throw new IllegalArgumentException("maxFrameSize must be in [16384,16777215]")
    if (initialWindowSize < 0 || initialWindowSize.toLong > Int.MaxValue)
      throw new IllegalArgumentException("initialWindowSize must be in [0, 2147483647]")
    List(
      Setting(Setting.HEADER_TABLE_SIZE, H2Settings.DefaultHeaderTableSize),
      Setting(Setting.MAX_CONCURRENT_STREAMS, maxConcurrentStreams.toLong),
      Setting(Setting.INITIAL_WINDOW_SIZE, initialWindowSize.toLong),
      Setting(Setting.MAX_FRAME_SIZE, maxFrameSize.toLong),
    ) ++ maxHeaderListSize.map(size => Setting(Setting.MAX_HEADER_LIST_SIZE, size.toLong)).toList
  }

  /**
   * One incomplete (CONTINUATION-fragmented) header block. Fragments are
   * buffered as a chunk list (newest first) and concatenated exactly once in
   * `toHeaders`, so N CONTINUATIONs cost O(total bytes) instead of the O(N *
   * total) quadratic copy a per-append `++` would pay within the cap. Empty
   * fragments contribute no bytes and are skipped, so a flood of empty
   * CONTINUATIONs cannot grow the list without tripping the byte cap either.
   * `totalLength` tracks the encoded bytes for the cap check without walking
   * the list. Byte content delivered to `toHeaders` is identical to eager
   * concatenation in arrival order.
   */
  private final case class PendingHeaders(
    streamId: Int,
    fragments: List[Chunk[Byte]],
    totalLength: Long,
    endStream: Boolean,
    priority: Option[Priority],
    padLength: Int,
  ) {
    def append(frame: H2Frame.Continuation, capBytes: Long): Option[PendingHeaders] =
      if (frame.headerBlock.isEmpty) Some(this)
      else {
        val next = totalLength + frame.headerBlock.length.toLong
        if (next > capBytes) None
        else Some(copy(fragments = frame.headerBlock :: fragments, totalLength = next))
      }

    def toHeaders: H2Frame.Headers = {
      val combined = fragments.reverse.foldLeft(Chunk.empty[Byte])(_ ++ _)
      H2Frame.Headers(streamId, combined, endStream, endHeaders = true, priority, padLength)
    }
  }

  private object PendingHeaders {
    def apply(frame: H2Frame.Headers): PendingHeaders =
      PendingHeaders(
        frame.streamId,
        List(frame.headerBlock),
        frame.headerBlock.length.toLong,
        frame.endStream,
        frame.priority,
        frame.padLength,
      )
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
