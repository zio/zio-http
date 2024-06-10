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

package zio.http

import java.nio.charset.Charset

import scala.annotation.tailrec

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.stream.{Take, ZStream}

import zio.http.StreamingForm.Buffer
import zio.http.internal.{FormAST, FormState}

final case class StreamingForm(source: ZStream[Any, Throwable, Byte], boundary: Boundary, bufferSize: Int = 8192) {
  def charset: Charset = boundary.charset

  /**
   * Runs the streaming form and collects all parts in memory, returning a Form
   */
  def collectAll(implicit trace: Trace): ZIO[Any, Throwable, Form] =
    fields.mapZIO {
      case sb: FormField.StreamingBinary =>
        sb.collect
      case other: FormField              =>
        ZIO.succeed(other)
    }.runCollect.map { formData =>
      Form(formData)
    }

  def fields(implicit trace: Trace): ZStream[Any, Throwable, FormField] =
    ZStream.unwrapScoped {
      implicit val unsafe: Unsafe = Unsafe.unsafe

      for {
        runtime    <- ZIO.runtime[Any]
        buffer     <- ZIO.succeed(new Buffer(bufferSize, crlfBoundary))
        abort      <- Promise.make[Nothing, Unit]
        fieldQueue <- Queue.bounded[Take[Throwable, FormField]](4)
        state  = initialState
        reader =
          source.runForeachChunk { bytes =>
            def handleBoundary(ast: Chunk[FormAST]): Option[FormField] =
              if (state.inNonStreamingPart) {
                FormField.fromFormAST(ast, charset) match {
                  case Right(formData) =>
                    buffer.reset()
                    state.reset
                    Some(formData)
                  case Left(e)         => throw e.asException
                }
              } else {
                buffer.reset()
                state.reset
                None
              }

            def handleByte(byte: Byte, isLastByte: Boolean): Option[FormField] = {
              state.formState match {
                case formState: FormState.FormStateBuffer =>
                  val nextFormState = formState.append(byte)
                  state.currentQueue match {
                    case Some(queue) =>
                      val takes = buffer.addByte(byte, isLastByte)
                      if (takes.nonEmpty) {
                        runtime.unsafe.run(queue.offerAll(takes).raceFirst(abort.await)).getOrThrowFiberFailure()
                      }
                    case None        =>
                  }
                  nextFormState match {
                    case newFormState: FormState.FormStateBuffer =>
                      if (
                        state.currentQueue.isEmpty &&
                        (newFormState.phase eq FormState.Phase.Part2) &&
                        !state.inNonStreamingPart
                      ) {
                        val contentType = FormField.getContentType(newFormState.tree)
                        if (contentType.binary) {
                          runtime.unsafe.run {
                            for {
                              newQueue <- Queue.bounded[Take[Nothing, Byte]](3)
                              _ <- newQueue.offer(Take.chunk(newFormState.tree.collect { case FormAST.Content(bytes) =>
                                bytes
                              }.flatten))
                              streamingFormData <- FormField
                                .incomingStreamingBinary(newFormState.tree, newQueue)
                                .mapError(_.asException)
                              _ = state.withCurrentQueue(newQueue)
                            } yield Some(streamingFormData)
                          }.getOrThrowFiberFailure()
                        } else {
                          val _ = state.withInNonStreamingPart(true)
                          None
                        }
                      } else {
                        None
                      }
                    case FormState.BoundaryEncapsulated(ast)     =>
                      handleBoundary(ast)
                    case FormState.BoundaryClosed(ast)           =>
                      handleBoundary(ast)
                  }
                case _                                    =>
                  None
              }
            }

            val builder = Chunk.newBuilder[FormField]
            val it      = bytes.iterator
            var hasNext = it.hasNext
            while (hasNext) {
              val byte = it.next()
              hasNext = it.hasNext
              handleByte(byte, !hasNext) match {
                case Some(field) => builder += field
                case _           => ()
              }
            }
            val fields  = builder.result()
            fieldQueue.offer(Take.chunk(fields)).when(fields.nonEmpty)
          }
        // FIXME: .blocking here is temporary until we figure out a better way to avoid running effects within mapAccumImmediate
        _ <- ZIO
          .blocking(reader)
          .catchAllCause(cause => fieldQueue.offer(Take.failCause(cause)))
          .ensuring(fieldQueue.offer(Take.end))
          .forkScoped
          .interruptible
        _ <- Scope.addFinalizerExit { exit =>
          // If the fieldStream fails, we need to make sure the reader stream can be interrupted, as it may be blocked
          // in the unsafe.run(queue.offer) call (interruption does not propagate into the unsafe.run). This is implemented
          // by setting the abort promise which is raced within the unsafe run when offering the element to the queue.
          abort.succeed(()).when(exit.isFailure)
        }
        fieldStream = ZStream.fromQueue(fieldQueue).flattenTake
      } yield fieldStream
    }

  private def initialState: StreamingForm.State =
    StreamingForm.initialState(boundary)

  private def crlfBoundary: Array[Byte] = Array[Byte](13, 10) ++ boundary.encapsulationBoundaryBytes.toArray
}

object StreamingForm {
  private final class State(
    val formState: FormState,
    private var _currentQueue: Option[Queue[Take[Nothing, Byte]]],
    private var _inNonStreamingPart: Boolean,
  ) {
    def currentQueue: Option[Queue[Take[Nothing, Byte]]] = _currentQueue
    def inNonStreamingPart: Boolean                      = _inNonStreamingPart

    def withCurrentQueue(queue: Queue[Take[Nothing, Byte]]): State = {
      _currentQueue = Some(queue)
      this
    }

    def withInNonStreamingPart(value: Boolean): State = {
      _inNonStreamingPart = value
      this
    }

    def reset: State = {
      _currentQueue = None
      _inNonStreamingPart = false
      formState.reset()
      this
    }
  }

  private def initialState(boundary: Boundary): State = {
    new State(FormState.fromBoundary(boundary), None, _inNonStreamingPart = false)
  }

  private final class Buffer(bufferSize: Int, crlfBoundary: Array[Byte]) {
    private var buffer: Array[Byte] = Array.ofDim(bufferSize)
    private var index: Int          = 0
    private val boundarySize        = crlfBoundary.length

    private def ensureHasCapacity(requiredCapacity: Int): Unit = {
      @tailrec
      def calculateNewCapacity(existing: Int, required: Int): Int = {
        val newCap = existing * 2
        if (newCap < required) calculateNewCapacity(newCap, required)
        else newCap
      }

      val l = buffer.length
      if (l <= requiredCapacity) {
        val newArray = Array.ofDim[Byte](calculateNewCapacity(l, requiredCapacity))
        java.lang.System.arraycopy(buffer, 0, newArray, 0, l)
        buffer = newArray
      } else ()
    }

    private def matchesPartialBoundary(idx: Int): Boolean = {
      val bs     = boundarySize
      var i      = 0
      var result = false
      while (i < bs && i <= idx && !result) {
        val i0 = idx - i
        var i1 = 0
        while (i >= i1 && buffer(i0 + i1) == crlfBoundary(i1) && !result) {
          if (i == i1) result = true
          i1 += 1
        }
        i += 1
      }
      result
    }

    def addByte(byte: Byte, isLastByte: Boolean): Chunk[Take[Nothing, Byte]] = {
      val idx = index
      ensureHasCapacity(idx + boundarySize + 1)
      buffer(idx) = byte
      index += 1

      var i                 = 0
      var foundFullBoundary = idx >= boundarySize - 1
      while (i < boundarySize && foundFullBoundary) {
        if (buffer(idx + 1 - crlfBoundary.length + i) != crlfBoundary(i)) {
          foundFullBoundary = false
        }
        i += 1
      }

      if (foundFullBoundary) {
        reset()
        val toTake = idx + 1 - boundarySize
        if (toTake == 0) Chunk(Take.end)
        else Chunk(Take.chunk(Chunk.fromArray(buffer.take(toTake))), Take.end)
      } else if (isLastByte && byte != '-' && !matchesPartialBoundary(idx)) {
        reset()
        Chunk(Take.chunk(Chunk.fromArray(buffer.take(idx + 1))))
      } else {
        Chunk.empty
      }
    }

    def reset(): Unit =
      index = 0
  }
}
