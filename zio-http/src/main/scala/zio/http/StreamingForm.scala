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

import zio.{Chunk, Queue, Trace, Unsafe, ZIO}

import zio.stream.{Take, ZStream}

import zio.http.StreamingForm.Buffer
import zio.http.internal.{FormAST, FormState}

final case class StreamingForm(source: ZStream[Any, Throwable, Byte], boundary: Boundary, bufferSize: Int = 8192) {
  def charset: Charset = boundary.charset

  /**
   * Runs the streaming form and collects all parts in memory, returning a Form
   */
  def collectAll: ZIO[Any, Throwable, Form] =
    fields
      .mapZIOPar(1) {
        case sb: FormField.StreamingBinary =>
          sb.collect
        case other: FormField              =>
          ZIO.succeed(other)
      }
      .runCollect
      .map { formData =>
        Form(formData)
      }

  def fields(implicit trace: Trace): ZStream[Any, Throwable, FormField] =
    ZStream
      .fromZIO(
        ZIO.runtime[Any].zip(ZIO.succeed(new Buffer(bufferSize))),
      )
      .flatMap { case (runtime, buffer) =>
        implicit val unsafe: Unsafe = Unsafe.unsafe

        source
          .mapAccum(initialState) { (state, byte) =>
            state.formState match {
              case formState: FormState.FormStateBuffer =>
                val nextFormState = formState.append(byte)
                state.currentQueue match {
                  case Some(queue) =>
                    val takes = buffer.addByte(crlfBoundary, byte)
                    if (takes.nonEmpty) {
                      runtime.unsafe.run(queue.offerAll(takes)).getOrThrowFiberFailure()
                    }
                  case None        =>
                }
                nextFormState match {
                  case newFormState: FormState.FormStateBuffer =>
                    if (
                      state.currentQueue.isEmpty &&
                      newFormState.phase == FormState.Phase.Part2 &&
                      !state.inNonStreamingPart
                    ) {
                      val contentType = FormField.getContentType(newFormState.tree)
                      if (contentType.binary) {
                        runtime.unsafe.run {
                          for {
                            newQueue <- Queue.unbounded[Take[Nothing, Byte]]
                            _ <- newQueue.offer(Take.chunk(newFormState.tree.collect { case FormAST.Content(bytes) =>
                              bytes
                            }.flatten))
                            streamingFormData <- FormField
                              .incomingStreamingBinary(newFormState.tree, newQueue)
                              .mapError(_.asException)
                            nextState = state.copy(
                              formState = newFormState,
                              currentQueue = Some(newQueue),
                            )
                          } yield (nextState, Some(streamingFormData))
                        }.getOrThrowFiberFailure()
                      } else {
                        val nextState = state.copy(formState = newFormState, inNonStreamingPart = true)
                        (nextState, None)
                      }
                    } else {
                      val nextState = state.copy(formState = newFormState)
                      (nextState, None)
                    }
                  case FormState.BoundaryEncapsulated(ast)     =>
                    if (state.inNonStreamingPart) {
                      runtime.unsafe.run {
                        FormField
                          .fromFormAST(ast, charset)
                          .mapBoth(
                            _.asException,
                            { formData =>
                              buffer.reset()
                              (state.reset, Some(formData))
                            },
                          )
                      }.getOrThrowFiberFailure()
                    } else {
                      buffer.reset()
                      (state.reset, None)
                    }
                  case FormState.BoundaryClosed(ast)           =>
                    if (state.inNonStreamingPart) {
                      runtime.unsafe.run {
                        FormField
                          .fromFormAST(ast, charset)
                          .mapBoth(
                            _.asException,
                            { formData =>
                              buffer.reset()
                              (state.reset, Some(formData))
                            },
                          )
                      }
                        .getOrThrowFiberFailure()
                    } else {
                      buffer.reset()
                      (state.reset, None)
                    }
                }

              case _ =>
                (state, None)
            }
          }
          .collect { case Some(formData) =>
            formData
          }
      }

  private def initialState: StreamingForm.State =
    StreamingForm.initialState(boundary)

  private val crlfBoundary: Chunk[Byte] = Chunk[Byte](13, 10) ++ boundary.encapsulationBoundaryBytes
}

object StreamingForm {
  private final case class State(
    boundary: Boundary,
    formState: FormState,
    currentQueue: Option[Queue[Take[Nothing, Byte]]],
    inNonStreamingPart: Boolean,
  ) {

    def reset: State =
      State(
        boundary,
        FormState.fromBoundary(boundary),
        None,
        inNonStreamingPart = false,
      )
  }

  private def initialState(boundary: Boundary): State =
    State(boundary, FormState.fromBoundary(boundary), None, inNonStreamingPart = false)

  private final class Buffer(bufferSize: Int) {
    private val buffer: Array[Byte] = new Array[Byte](bufferSize)
    private var length: Int         = 0

    def addByte(
      crlfBoundary: Chunk[Byte],
      byte: Byte,
    ): Chunk[Take[Nothing, Byte]] = {
      buffer(length) = byte
      if (length < (crlfBoundary.length - 1)) {
        // Not enough bytes to check if we have the boundary
        length += 1
        Chunk.empty
      } else {
        val foundBoundary = Chunk.fromArray(buffer).slice(length + 1 - crlfBoundary.length, length + 1) == crlfBoundary

        if (foundBoundary) {
          // We have found the boundary
          val preBoundary =
            Chunk.fromArray(Chunk.fromArray(buffer).take(length + 1 - crlfBoundary.length).toArray[Byte])
          length = 0
          Chunk(Take.chunk(preBoundary), Take.end)
        } else {
          // We don't have the boundary
          if (length < (buffer.length - 2)) {
            length += 1
            Chunk.empty
          } else {
            val preBoundary =
              Chunk.fromArray(Chunk.fromArray(buffer).take(length + 1 - crlfBoundary.length).toArray[Byte])
            for (i <- crlfBoundary.indices) {
              buffer(i) = buffer(length + 1 - crlfBoundary.length + i)
            }
            length = crlfBoundary.length
            Chunk(Take.chunk(preBoundary))
          }
        }
      }
    }

    def reset(): Unit = {
      length = 0
    }
  }
}
