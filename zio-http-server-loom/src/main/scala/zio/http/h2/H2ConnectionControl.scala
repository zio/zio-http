package zio.http.h2

import java.io.OutputStream
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger, AtomicLong, AtomicReference}
import java.util.concurrent.{
  CancellationException,
  ConcurrentHashMap,
  CountDownLatch,
  ExecutionException,
  ScheduledFuture,
  TimeUnit,
  TimeoutException,
}

import scala.annotation.experimental
import scala.util.control.NonFatal

import zio.blocks.chunk.Chunk
import zio.blocks.mux.{Mux, MuxError}

@experimental
final class H2ConnectionControl(
  output: OutputStream,
  mux: Mux[Int, H2Frame, H2Frame],
  idleTimeoutMs: Long = 60000,
  requestTimeoutMs: Long = 30000,
) {
  private val writeLock         = new Object
  private val goingAwayState    = new AtomicBoolean(false)
  private val closedState       = new AtomicBoolean(false)
  private val lastActivityNanos = new AtomicLong(System.nanoTime())
  private val lastStreamIdState = new AtomicInteger(Int.MaxValue)
  private val idleTimerState    = new AtomicBoolean(false)
  private val idleTimerThread   = new AtomicReference[Thread](null)
  private val trackedStreams    = new ConcurrentHashMap[Int, java.lang.Boolean]()

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
  def startIdleTimer(): Unit                          =
    if (idleTimeoutMs > 0L && idleTimerState.compareAndSet(false, true)) {
      lastActivityNanos.set(System.nanoTime())
      val thread = Thread
        .ofVirtual()
        .name("zio-http-h2-idle-timeout")
        .start(runnable(idleTimerLoop()))
      idleTimerThread.set(thread)
    }

  def resetIdleTimer(): Unit                               = {
    lastActivityNanos.set(System.nanoTime())
    val thread = idleTimerThread.get()
    if (thread != null) thread.interrupt()
  }
  def startRequestTimer(streamId: Int): ScheduledFuture[?] = {
    val future = new VirtualTimerFuture(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMs.max(0L)))
    if (requestTimeoutMs <= 0L) {
      future.complete()
      return future
    }
    val thread = Thread
      .ofVirtual()
      .name("zio-http-h2-request-timeout-" + streamId)
      .start(runnable {
        try {
          TimeUnit.MILLISECONDS.sleep(requestTimeoutMs)
          if (!future.isCancelled && isStreamOpen(streamId)) sendRstStream(streamId, H2Error.Code.CANCEL)
          future.complete()
        } catch {
          case _: InterruptedException =>
            if (future.isCancelled) future.complete()
            else {
              Thread.currentThread().interrupt()
              future.complete()
            }
          case NonFatal(error)         => future.fail(error)
        }
      })
    future.attach(thread)
    future
  }

  private def idleTimerLoop(): Unit                   = {
    val timeoutNanos = TimeUnit.MILLISECONDS.toNanos(idleTimeoutMs)
    while (!closedState.get()) {
      val elapsed   = System.nanoTime() - lastActivityNanos.get()
      val remaining = timeoutNanos - elapsed
      if (remaining <= 0L) {
        if (System.nanoTime() - lastActivityNanos.get() >= timeoutNanos) {
          sendGoAway(Int.MaxValue, H2Error.Code.NO_ERROR)
          closeConnection(MuxError.Cancelled("connection", "idle timeout"))
          return
        }
      } else {
        try TimeUnit.NANOSECONDS.sleep(remaining)
        catch {
          case _: InterruptedException => ()
        }
      }
    }
  }
  private def closeConnection(reason: MuxError): Unit =
    if (closedState.compareAndSet(false, true)) {
      mux.closeAll(reason)
      closeQuietly(output)
      val thread = idleTimerThread.get()
      if (thread != null && (thread ne Thread.currentThread())) thread.interrupt()
    }

  private def writeFrame(frame: H2Frame, flush: Boolean): Unit              = {
    val bytes = FrameCodec.encode(frame).toArray
    writeLock.synchronized {
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
    private val threadRef                                            = new AtomicReference[Thread](null)
    private val failureRef                                           = new AtomicReference[Throwable](null)
    private val doneLatch                                            = new CountDownLatch(1)
    def attach(thread: Thread): Unit                                 = threadRef.set(thread)
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
          if (mayInterruptIfRunning) {
            val thread = threadRef.get()
            if (thread != null) thread.interrupt()
          }
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
