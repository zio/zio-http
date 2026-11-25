/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.h2

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.{Condition, ReentrantLock}
import java.util.concurrent.TimeUnit

/**
 * Tracks HTTP/2 flow-control windows (RFC 9113 section 6.9): one
 * connection-level window plus one per-stream window.
 *
 * `consumeSendWindow` parks the calling Loom virtual thread on a Condition
 * under backpressure (no spin, no ZIO fiber involved). The park is always
 * bounded (see `DefaultSendWindowTimeoutMs`): apart from WINDOW_UPDATE and
 * `removeStream` there is no other external signaler, so an unbounded wait
 * would let a dead peer park a virtual thread forever. On expiry the waiter
 * gets a [[FlowController.FlowControlTimeout]] and the caller is expected to
 * reset the stream; windows are untouched.
 *
 * jvm-perf notes: the wait stays a Condition park (no spinning); lengths and
 * deadlines are primitive `long`/`int` (no boxing); the hot consume path makes
 * no megamorphic calls.
 */
final class FlowController(initialConnectionWindow: Int, initialStreamWindow: Int) {
  FlowController.requireValidInitialWindow(initialConnectionWindow, "connection")
  FlowController.requireValidInitialWindow(initialStreamWindow, "stream")

  private val lock                  = new ReentrantLock(true)
  private val connectionUpdated     = lock.newCondition()
  private val connectionWindowValue = new AtomicInteger(initialConnectionWindow)
  private val streamStates          = new ConcurrentHashMap[Int, FlowController.StreamState]()

  def connectionWindow: Int = connectionWindowValue.get()

  def streamWindow(streamId: Int): Int = {
    val state = streamStates.get(streamId)
    if (state.eq(null)) throw new NoSuchElementException("Unknown HTTP/2 stream: " + streamId)
    state.window.get()
  }

  def consumeSendWindow(streamId: Int, bytes: Int): Unit =
    consumeSendWindow(streamId, bytes, FlowController.DefaultSendWindowTimeoutMs)

  /**
   * Bounded variant of [[consumeSendWindow]]: waits at most `timeoutMs` for
   * connection- and stream-level window, then throws
   * [[FlowController.FlowControlTimeout]] leaving both windows untouched. A
   * late WINDOW_UPDATE still resumes the waiter normally as long as the
   * deadline has not passed (no spurious timeout).
   */
  def consumeSendWindow(streamId: Int, bytes: Int, timeoutMs: Long): Unit = {
    require(bytes >= 0, "Flow-control bytes must be non-negative")
    if (bytes == 0) return

    lock.lock()
    try {
      val state    = requireStreamState(streamId)
      val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.max(0L))
      while (connectionWindowValue.get() < bytes || state.window.get() < bytes) {
        val remaining = deadline - System.nanoTime()
        if (remaining <= 0L) throw new FlowController.FlowControlTimeout(streamId, bytes, timeoutMs)
        // Await on the window that is actually short so a WINDOW_UPDATE for
        // the other level cannot wake us spuriously; the loop re-checks both.
        if (connectionWindowValue.get() < bytes) connectionUpdated.awaitNanos(remaining)
        else state.updated.awaitNanos(remaining)
        ensureStreamRegistered(streamId, state)
      }
      connectionWindowValue.addAndGet(-bytes)
      state.window.addAndGet(-bytes)
    } catch {
      case interrupted: InterruptedException =>
        Thread.currentThread().interrupt()
        throw new IllegalStateException("Interrupted while waiting for HTTP/2 flow-control window", interrupted)
    } finally lock.unlock()
  }

  def applyWindowUpdate(streamId: Int, increment: Int): Unit = {
    require(increment > 0, "WINDOW_UPDATE increment must be positive")

    lock.lock()
    try {
      if (streamId == 0) {
        val nextWindow = FlowController.checkedIncrement(connectionWindowValue.get(), increment)
        connectionWindowValue.set(nextWindow)
        connectionUpdated.signalAll()
        signalAllStreams()
      } else {
        val state      = requireStreamState(streamId)
        val nextWindow = FlowController.checkedIncrement(state.window.get(), increment)
        state.window.set(nextWindow)
        state.updated.signalAll()
      }
    } finally lock.unlock()
  }

  def registerStream(streamId: Int): Unit = {
    lock.lock()
    try {
      val previous =
        streamStates.put(streamId, new FlowController.StreamState(initialStreamWindow, lock.newCondition()))
      if (previous.ne(null)) previous.updated.signalAll()
      connectionUpdated.signalAll()
    } finally lock.unlock()
  }

  def removeStream(streamId: Int): Unit = {
    lock.lock()
    try {
      val state = streamStates.remove(streamId)
      if (state.ne(null)) state.updated.signalAll()
      connectionUpdated.signalAll()
    } finally lock.unlock()
  }

  private def requireStreamState(streamId: Int): FlowController.StreamState = {
    val state = streamStates.get(streamId)
    if (state.eq(null)) throw new NoSuchElementException("Unknown HTTP/2 stream: " + streamId)
    state
  }

  private def ensureStreamRegistered(streamId: Int, state: FlowController.StreamState): Unit = {
    val current = streamStates.get(streamId)
    if (current.eq(null) || current.ne(state)) throw new NoSuchElementException("Unknown HTTP/2 stream: " + streamId)
  }

  private def signalAllStreams(): Unit = {
    val iterator = streamStates.values().iterator()
    while (iterator.hasNext) iterator.next().updated.signalAll()
  }
}

object FlowController {
  private val MaxWindowSize = Int.MaxValue

  /**
   * Default bound for a `consumeSendWindow` park (30s). Design choice: long
   * enough that a merely slow receiver topping up windows never trips it
   * (steady H2 transfers top up every ~16KB, i.e. milliseconds), short enough
   * that a dead peer cannot park a virtual thread — and its 16KBChunk staging —
   * forever. Distinct from a protocol violation: expiry is a local abort
   * (callers reset with CANCEL), never FLOW_CONTROL_ERROR, which is reserved
   * for actual window-overflow violations (RFC 9113 section 6.9.1).
   */
  val DefaultSendWindowTimeoutMs: Long = 30000L

  private def checkedIncrement(window: Int, increment: Int): Int = {
    val nextWindow = window.toLong + increment.toLong
    if (nextWindow > MaxWindowSize.toLong) throw new FlowControlException("HTTP/2 flow-control window exceeded 2^31-1")
    nextWindow.toInt
  }

  private def requireValidInitialWindow(window: Int, name: String): Unit =
    require(window >= 0 && window <= MaxWindowSize, "Initial " + name + " window must be in [0, 2^31-1]")

  final class FlowControlException(message: String)
      extends IllegalStateException(message + " (" + H2Error.Code.FLOW_CONTROL_ERROR.value + ")")

  /**
   * A `consumeSendWindow` park outlived its bound with no WINDOW_UPDATE and no
   * `removeStream`. Local abort signal, not a peer protocol violation: the send
   * path maps it to RST_STREAM(CANCEL), reusing the single T5 RST send site.
   */
  final class FlowControlTimeout(streamId: Int, bytes: Int, timeoutMs: Long)
      extends IllegalStateException(
        s"Timed out after ${timeoutMs}ms waiting for $bytes flow-control window bytes on stream $streamId",
      ) {
    override def fillInStackTrace(): Throwable = this
  }

  private final class StreamState(initialWindow: Int, val updated: Condition) {
    val window: AtomicInteger = new AtomicInteger(initialWindow)
  }
}
