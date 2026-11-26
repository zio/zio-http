package zio.http.h2

import java.io.OutputStream
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.{
  CancellationException,
  ConcurrentHashMap,
  CountDownLatch,
  ExecutionException,
  ScheduledFuture,
  ScheduledThreadPoolExecutor,
  ThreadFactory,
  TimeUnit,
  TimeoutException,
}

import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.mux.{Mux, MuxError}

import zio.http.Connector

final class H2ConnectionControl(
  output: OutputStream,
  mux: Mux[Int, H2Frame, H2Frame],
  idleTimeoutMs: Long = 60000,
  requestTimeoutMs: Long = 30000,
  writeLock: Object = null,
  lastStreamId: () => Int = () => 0,
  drainTimeoutMs: Long = 1000,
  onGracefulShutdown: () => Unit = () => (),
) {
  // Single shared writer lock: the live H2Connection passes its own
  // writeLock here so GOAWAY/RST_STREAM bytes and the connection's response
  // bytes never interleave on the OutputStream. A null lock (unit tests)
  // falls back to a private lock.
  private val effectiveWriteLock: Object =
    if (writeLock != null) writeLock else new Object
  private val goingAwayState             = new AtomicBoolean(false)
  private val closedState                = new AtomicBoolean(false)
  private val lastActivityNanos          = new AtomicLong(System.nanoTime())
  private val lastStreamIdState          = new AtomicInteger(Int.MaxValue)
  private val idleTimerState             = new AtomicBoolean(false)
  private val idleFuture                 = new AtomicReference[ScheduledFuture[?]](null)
  private val trackedStreams             = new ConcurrentHashMap[Int, java.lang.Boolean]()

  def trackStream(streamId: Int): Unit   = trackedStreams.put(streamId, java.lang.Boolean.TRUE)
  def untrackStream(streamId: Int): Unit = trackedStreams.remove(streamId)

  def sendGoAway(lastStreamId: Int, errorCode: H2Error.Code, debug: Chunk[Byte] = Chunk.empty): Unit = {
    writeFrame(H2Frame.GoAway(lastStreamId, errorCode, debug), flush = true)
    goingAwayState.set(true)
    recordLastStreamId(lastStreamId)
  }

  def handleGoAway(frame: H2Frame.GoAway): Unit = {
    goingAwayState.set(true)
    recordLastStreamId(frame.lastStreamId)
    cancelStreamsAbove(frame.lastStreamId, MuxError.Cancelled("connection", "GOAWAY " + frame.errorCode))
  }

  def sendRstStream(streamId: Int, errorCode: H2Error.Code): Unit = {
    writeFrame(H2Frame.RstStream(streamId, errorCode), flush = true)
    mux.cancel(streamId, MuxError.Cancelled(streamId, errorCode.toString))
  }

  def handleRstStream(frame: H2Frame.RstStream): Unit =
    mux.cancel(frame.streamId, MuxError.Cancelled(frame.streamId, frame.errorCode.toString))
  def isGoingAway: Boolean                            = goingAwayState.get()
  def lastPeerStreamId: Int                           = lastStreamIdState.get()
  def getWriteLock: Object                            = effectiveWriteLock
  def startIdleTimer(): Unit                          =
    if (idleTimeoutMs > 0L && idleTimerState.compareAndSet(false, true)) {
      lastActivityNanos.set(System.nanoTime())
      scheduleIdle(idleTimeoutMs)
    }

  /** Schedules the single idle-deadline task on the shared timer. */
  private def scheduleIdle(delayMs: Long): Unit =
    if (!closedState.get()) {
      val future   = H2ConnectionControl.scheduler.schedule(
        runnable(idleTimerTick()),
        delayMs.max(0L),
        TimeUnit.MILLISECONDS,
      )
      val previous = idleFuture.getAndSet(future)
      if (previous != null) previous.cancel(false)
    }

  /** Cheap activity update: no thread wake-up, no reschedule. */
  def resetIdleTimer(): Unit =
    lastActivityNanos.set(System.nanoTime())

  /**
   * Stops the idle timer thread. Safe to call from any thread, including the
   * timer thread itself (self-interrupt is skipped).
   */
  def stopIdleTimer(): Unit = {
    closedState.set(true)
    val future = idleFuture.getAndSet(null)
    if (future != null) future.cancel(false)
  }

  /**
   * Generic stream completion deadline on a virtual thread (same
   * `VirtualTimerFuture` pattern as `startRequestTimer`).
   *
   * Sleeps `timeoutMs` then resets `streamId` with `errorCode` iff
   * `stillPending()` holds. `stillPending()` and (when `requireOpenStream`)
   * `isStreamOpen()` are re-checked immediately before the wire write: the
   * stream may have completed in the gap between wake-up and the send, and an
   * RST after completion would kill a healthy stream. Pre-open deadlines (an
   * incomplete header block has no mux entry yet) pass
   * `requireOpenStream = false` and predicate purely on `stillPending()`. The
   * caller cancels the returned future when the stream completes, so healthy
   * streams pay no lingering thread. Never uses ZIO fibers for the blocking
   * sleep.
   */
  def scheduleTimeoutRst(
    streamId: Int,
    timeoutMs: Long,
    errorCode: H2Error.Code,
    stillPending: () => Boolean,
    requireOpenStream: Boolean = true,
  ): ScheduledFuture[?] = {
    val future    = new VirtualTimerFuture(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.max(0L)))
    if (timeoutMs <= 0L) {
      future.complete()
      return future
    }
    // Scheduled on the shared timer (no VT per request). Only if the deadline
    // actually fires do we spawn a short-lived VT: the RST write blocks on the
    // socket and must never stall the single timer thread.
    val scheduled = H2ConnectionControl.scheduler.schedule(
      runnable {
        if (future.isCancelled) future.complete()
        else {
          val task = Thread
            .ofVirtual()
            .name("zio-http-h2-stream-timeout-" + streamId)
            .unstarted(runnable {
              try {
                // Re-check at send time: the stream may have completed between
                // the deadline firing and the wire write below.
                if (!future.isCancelled && stillPending() && (!requireOpenStream || isStreamOpen(streamId))) {
                  try sendRstStream(streamId, errorCode)
                  catch { case NonFatal(_) => () }
                }
                future.complete()
              } catch {
                case NonFatal(error) => future.fail(error)
              }
            })
          task.start()
        }
      },
      timeoutMs,
      TimeUnit.MILLISECONDS,
    )
    future.attachScheduled(scheduled)
    future
  }

  def startRequestTimer(streamId: Int): ScheduledFuture[?] = {
    val future    = new VirtualTimerFuture(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs.max(0L)))
    if (requestTimeoutMs <= 0L) {
      future.complete()
      return future
    }
    val scheduled = H2ConnectionControl.scheduler.schedule(
      runnable {
        if (future.isCancelled) future.complete()
        else {
          val task = Thread
            .ofVirtual()
            .name("zio-http-h2-request-timeout-" + streamId)
            .unstarted(runnable {
              try {
                if (!future.isCancelled && isStreamOpen(streamId)) sendRstStream(streamId, H2Error.Code.CANCEL)
                future.complete()
              } catch {
                case NonFatal(error) => future.fail(error)
              }
            })
          task.start()
        }
      },
      requestTimeoutMs,
      TimeUnit.MILLISECONDS,
    )
    future.attachScheduled(scheduled)
    future
  }

  private def idleTimerTick(): Unit =
    if (!closedState.get()) {
      val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeoutMs)
      val elapsed      = System.nanoTime() - lastActivityNanos.get()
      if (elapsed >= timeoutNanos) fireIdleTimeout()
      else scheduleIdle(TimeUnit.NANOSECONDS.toMillis(timeoutNanos - elapsed).max(1L))
    }

  /**
   * RFC 9113 6.8 idle close. Runs the GOAWAY + bounded drain off the shared
   * timer thread (both do blocking I/O / a drain loop): one VT per idle-timeout
   * event (at most once per connection lifetime), never one per connection.
   */
  private def fireIdleTimeout(): Unit                 = {
    val drain = Thread
      .ofVirtual()
      .name("zio-http-h2-idle-shutdown")
      .unstarted(runnable {
        if (goingAwayState.compareAndSet(false, true)) {
          try sendGoAway(lastStreamId(), H2Error.Code.NO_ERROR)
          catch { case NonFatal(_) => () }
        }
        try onGracefulShutdown()
        catch { case NonFatal(_) => () }
        try closeConnection(MuxError.Cancelled("connection", "idle timeout"))
        catch { case NonFatal(_) => () }
      })
    drain.start()
  }
  private def closeConnection(reason: MuxError): Unit =
    if (closedState.compareAndSet(false, true)) {
      mux.closeAll(reason)
      closeQuietly(output)
      val future = idleFuture.getAndSet(null)
      if (future != null) future.cancel(false)
    }

  private def writeFrame(frame: H2Frame, flush: Boolean): Unit              = {
    val bytes = FrameCodec.encode(frame).toArray
    effectiveWriteLock.synchronized {
      output.write(bytes)
      if (flush) output.flush()
    }
  }
  private def recordLastStreamId(streamId: Int): Unit                       = {
    var done = false
    while (!done) {
      val current = lastStreamIdState.get()
      if (streamId >= current) done = true
      else done = lastStreamIdState.compareAndSet(current, streamId)
    }
  }
  private def isStreamOpen(streamId: Int): Boolean                          =
    mux.get(streamId).exists(!_.isClosed)
  private def cancelStreamsAbove(lastStreamId: Int, reason: MuxError): Unit = {
    val iter = trackedStreams.keySet().iterator()
    while (iter.hasNext) {
      val streamId = iter.next()
      if (streamId > lastStreamId) mux.cancel(streamId, reason)
    }
  }
  private def runnable(body: => Unit): Runnable                             =
    new Runnable {
      def run(): Unit = body
    }
  private def closeQuietly(resource: AutoCloseable): Unit                   =
    if (resource != null) {
      try resource.close()
      catch {
        case NonFatal(_) => ()
      }
    }
  private final class VirtualTimerFuture(deadlineNanos: Long) extends ScheduledFuture[Unit] {
    private val doneRef                                              = new AtomicBoolean(false)
    private val cancelledRef                                         = new AtomicBoolean(false)
    private val threadRef                                            = new AtomicReference[ScheduledFuture[?]](null)
    private val failureRef                                           = new AtomicReference[Throwable](null)
    private val doneLatch                                            = new CountDownLatch(1)
    def attachScheduled(future: ScheduledFuture[?]): Unit            = {
      threadRef.set(future)
      if (cancelledRef.get()) future.cancel(false)
    }
    def complete(): Unit                                             =
      if (doneRef.compareAndSet(false, true)) doneLatch.countDown()
    def fail(error: Throwable): Unit                                 = {
      failureRef.compareAndSet(null, error)
      complete()
    }
    override def getDelay(unit: TimeUnit): Long                      =
      unit.convert(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS)
    override def compareTo(other: java.util.concurrent.Delayed): Int = {
      val diff = getDelay(TimeUnit.NANOSECONDS) - other.getDelay(TimeUnit.NANOSECONDS)
      if (diff < 0L) -1 else if (diff > 0L) 1 else 0
    }
    override def cancel(mayInterruptIfRunning: Boolean): Boolean     = {
      if (doneRef.get()) false
      else {
        val cancelled = cancelledRef.compareAndSet(false, true)
        if (cancelled) {
          val scheduled = threadRef.get()
          if (scheduled != null) scheduled.cancel(false)
          complete()
        }
        cancelled
      }
    }
    override def isCancelled: Boolean                                = cancelledRef.get()
    override def isDone: Boolean                                     = doneRef.get()
    override def get(): Unit                                         = {
      doneLatch.await()
      rethrowIfNeeded()
      ()
    }
    override def get(timeout: Long, unit: TimeUnit): Unit            = {
      if (!doneLatch.await(timeout, unit)) throw new TimeoutException("HTTP/2 request timer did not complete in time")
      rethrowIfNeeded()
      ()
    }
    private def rethrowIfNeeded(): Unit                              = {
      if (isCancelled) throw new CancellationException("HTTP/2 request timer cancelled")
      val failure = failureRef.get()
      if (failure != null) throw new ExecutionException(failure)
    }
  }
}

object H2ConnectionControl {

  /**
   * Shared deadline scheduler. One daemon platform thread services every
   * connection's idle deadline, so an idle connection costs O(1) heap for its
   * scheduled task instead of a dedicated virtual thread (~3 VTs/conn -> 2
   * VTs/conn with the writer still parked). Tasks are cheap: on fire they
   * either reschedule at the remaining time or hand GOAWAY/drain to a fresh
   * virtual thread, so the timer thread never blocks on socket I/O.
   */
  private val scheduler: ScheduledThreadPoolExecutor = {
    val factory: ThreadFactory = new ThreadFactory {
      private val counter                = new AtomicInteger(0)
      def newThread(r: Runnable): Thread = {
        val thread = new Thread(r, "zio-http-h2-timer-" + counter.incrementAndGet())
        thread.setDaemon(true)
        thread
      }
    }
    val executor               = new ScheduledThreadPoolExecutor(1, factory)
    executor.setRemoveOnCancelPolicy(true)
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
    executor
  }

  /**
   * The single conversion site from [[Connector.idleTimeout]] (a
   * [[java.time.Duration]]) to the control's idle timeout in milliseconds. All
   * live-path wiring must go through here; never convert inline.
   */
  def idleTimeoutMs(connector: Connector): Long =
    connector.idleTimeout.toMillis
}
