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
    ZStream.fromZIO(ZIO.runtime[Any]).flatMap { runtime =>
      implicit val unsafe: Unsafe = Unsafe.unsafe

      source
        .mapAccum(initialState) { (state, byte) =>
          state.formState match {
            case formState: FormState.FormStateBuffer =>
              val nextFormState = formState.append(byte)
              val newBuffer     =
                state.currentQueue match {
                  case Some(queue) =>
                    val (newBuffer, takes) = state.buffer.addByte(crlfBoundary, byte)
                    if (takes.isEmpty) newBuffer
                    else {
                      // println(s"takes: $takes")
                      runtime.unsafe.run(queue.offerAll(takes)).getOrThrowFiberFailure()
                      newBuffer
                    }
                  case None        =>
                    state.buffer
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
                            buffer = newBuffer,
                          )
                        } yield (nextState, Some(streamingFormData))
                      }.getOrThrowFiberFailure()
                    } else {
                      val nextState = state.copy(formState = newFormState, inNonStreamingPart = true)
                      (nextState, None)
                    }
                  } else {
                    val nextState = state.copy(formState = newFormState, buffer = newBuffer)
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
                            (state.reset, Some(formData))
                          },
                        )
                    }.getOrThrowFiberFailure()
                  } else {
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
                            (state.reset, Some(formData))
                          },
                        )
                    }
                      .getOrThrowFiberFailure()
                  } else {
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
    StreamingForm.initialState(boundary, bufferSize)

  private val crlfBoundary: Chunk[Byte] = Chunk[Byte](13, 10) ++ boundary.encapsulationBoundaryBytes
}

object StreamingForm {
  private final case class State(
    boundary: Boundary,
    formState: FormState,
    currentQueue: Option[Queue[Take[Nothing, Byte]]],
    buffer: Buffer,
    inNonStreamingPart: Boolean,
  ) {

    def reset: State =
      State(
        boundary,
        FormState.fromBoundary(boundary),
        None,
        Buffer.empty(buffer.buffer.length),
        inNonStreamingPart = false,
      )
  }

  private def initialState(boundary: Boundary, bufferSize: Int): State =
    State(boundary, FormState.fromBoundary(boundary), None, Buffer.empty(bufferSize), inNonStreamingPart = false)

  private case class Buffer(buffer: Array[Byte], length: Int) {
    def addByte(
      crlfBoundary: Chunk[Byte],
      byte: Byte,
    ): (Buffer, Chunk[Take[Nothing, Byte]]) = {
      buffer(length) = byte
      if (length < (crlfBoundary.length - 1)) {
        // Not enough bytes to check if we have the boundary
        (this.copy(length = length + 1), Chunk.empty)
      } else {
        val foundBoundary = Chunk.fromArray(buffer).slice(length + 1 - crlfBoundary.length, length + 1) == crlfBoundary

        if (foundBoundary) {
          // We have found the boundary
          val preBoundary =
            Chunk.fromArray(Chunk.fromArray(buffer).take(length + 1 - crlfBoundary.length).toArray[Byte])
          (this.copy(length = 0), Chunk(Take.chunk(preBoundary), Take.end))
        } else {
          // We don't have the boundary
          if (length < (buffer.length - 2)) {
            (this.copy(length = length + 1), Chunk.empty)
          } else {
            val preBoundary =
              Chunk.fromArray(Chunk.fromArray(buffer).take(length + 1 - crlfBoundary.length).toArray[Byte])
            for (i <- crlfBoundary.indices) {
              buffer(i) = buffer(length + 1 - crlfBoundary.length + i)
            }
            (this.copy(length = crlfBoundary.length), Chunk(Take.chunk(preBoundary)))
          }
        }
      }
    }
  }

  private object Buffer {
    def empty(bufferSize: Int): Buffer = Buffer(new Array[Byte](bufferSize), 0)
  }
}
