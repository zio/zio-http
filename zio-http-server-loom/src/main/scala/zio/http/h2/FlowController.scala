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

  def consumeSendWindow(streamId: Int, bytes: Int): Unit = {
    require(bytes >= 0, "Flow-control bytes must be non-negative")
    if (bytes == 0) return

    lock.lock()
    try {
      val state = requireStreamState(streamId)
      while (connectionWindowValue.get() < bytes || state.window.get() < bytes) {
        if (connectionWindowValue.get() < bytes) connectionUpdated.await()
        else state.updated.await()
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

  private def checkedIncrement(window: Int, increment: Int): Int = {
    val nextWindow = window.toLong + increment.toLong
    if (nextWindow > MaxWindowSize.toLong) throw new FlowControlException("HTTP/2 flow-control window exceeded 2^31-1")
    nextWindow.toInt
  }

  private def requireValidInitialWindow(window: Int, name: String): Unit =
    require(window >= 0 && window <= MaxWindowSize, "Initial " + name + " window must be in [0, 2^31-1]")

  final class FlowControlException(message: String)
      extends IllegalStateException(message + " (" + H2Error.Code.FLOW_CONTROL_ERROR.value + ")")

  private final class StreamState(initialWindow: Int, val updated: Condition) {
    val window: AtomicInteger = new AtomicInteger(initialWindow)
  }
}
