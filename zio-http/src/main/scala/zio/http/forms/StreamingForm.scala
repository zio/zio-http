package zio.http.forms

import zio.{Chunk, Queue, ZIO}
import zio.stream.{Take, ZStream}

import java.nio.charset.Charset
import scala.collection.immutable

final case class StreamingForm(source: ZStream[Any, Throwable, Byte], boundary: Boundary, charset: Charset) {

  def collectAll(): ZIO[Any, Throwable, Form] =
    data
      .mapZIOPar(1) {
        case sb: FormData.StreamingBinary =>
          sb.collect()
        case other: FormData              =>
          ZIO.succeed(other)
      }
      .runCollect
      .map { formData =>
        Form(formData)
      }

  def data: ZStream[Any, Throwable, FormData] =
    source
      .mapAccumZIO(initialState) { (state, byte) =>
        state.formState match {
          case formState: FormState.FormStateBuffer =>
            val nextFormState = formState.append(byte)
            (state.currentQueue match {
              case Some(queue) =>
                val (newBuffer, maybeTake) = addByteToBuffer(state.buffer, byte)
                maybeTake match {
                  case Some(take) => queue.offer(take).as(newBuffer)
                  case None       => ZIO.succeed(newBuffer)
                }
              case None        =>
                ZIO.succeed(state.buffer)
            }).flatMap { newBuffer =>
              nextFormState match {
                case newFormState: FormState.FormStateBuffer =>
                  if (
                    state.currentQueue.isEmpty &&
                    newFormState.phase == FormState.Phase.Part2 &&
                    !state.inNonStreamingPart
                  ) {
                    val contentType = FormData.getContentType(newFormState.tree)
                    if (contentType.binary) {
                      for {
                        newQueue <- Queue.unbounded[Take[Nothing, Byte]]
                        _        <- newQueue.offer(Take.chunk(newFormState.tree.collect { case FormAST.Content(bytes) =>
                          bytes
                        }.flatten))
                        streamingFormData <- FormData.streamingBody(newFormState.tree, newQueue).mapError(_.asException)
                        nextState = state.copy(
                          formState = newFormState,
                          currentQueue = Some(newQueue),
                          buffer = newBuffer,
                        )
                      } yield (nextState, Some(streamingFormData))
                    } else {
                      val nextState = state.copy(formState = newFormState, inNonStreamingPart = true)
                      ZIO.succeed((nextState, None))
                    }
                  } else {
                    val nextState = state.copy(formState = newFormState, buffer = newBuffer)
                    ZIO.succeed((nextState, None))
                  }
                case FormState.BoundaryEncapsulated(ast)     =>
                  if (state.inNonStreamingPart) {
                    FormData
                      .fromFormAST(ast, charset)
                      .mapBoth(
                        _.asException,
                        { formData =>
                          (state.reset, Some(formData))
                        },
                      )
                  } else {
                    ZIO.succeed((state.reset, None))
                  }
                case FormState.BoundaryClosed(ast)           =>
                  if (state.inNonStreamingPart) {
                    FormData
                      .fromFormAST(ast, charset)
                      .mapBoth(
                        _.asException,
                        { formData =>
                          (state.reset, Some(formData))
                        },
                      )
                  } else {
                    ZIO.succeed((state.reset, None))
                  }
              }
            }
          case _                                    =>
            ZIO.succeed(state, None)
        }
      }
      .collect { case Some(formData) =>
        formData
      }

  private def initialState: StreamingForm.State =
    StreamingForm.initialState(boundary)

  private val crlfBoundary: Chunk[Byte] = Chunk[Byte](13, 10) ++ boundary.encapsulationBoundaryBytes

  private def addByteToBuffer(
    buffer: immutable.Queue[Byte],
    byte: Byte,
  ): (immutable.Queue[Byte], Option[Take[Nothing, Byte]]) =
    if (buffer.length < (crlfBoundary.length - 1)) {
      // Not enough bytes to check if we have the boundary
      (buffer.enqueue(byte), None)
    } else {
      val newBuffer      = buffer.enqueue(byte)
      val newBufferChunk = Chunk.fromIterable(newBuffer)
      if (newBufferChunk == crlfBoundary) {
        // We have found the boundary
        (immutable.Queue.empty, Some(Take.end))
      } else {
        // We don't have the boundary
        val (out, remaining) = newBuffer.dequeue
        (remaining, Some(Take.single(out)))
      }
    }
}

object StreamingForm {
  private final case class State(
    boundary: Boundary,
    formState: FormState,
    currentQueue: Option[Queue[Take[Nothing, Byte]]],
    buffer: immutable.Queue[Byte],
    inNonStreamingPart: Boolean,
  ) {

    def reset: State =
      State(boundary, FormState.fromBoundary(boundary), None, immutable.Queue.empty, inNonStreamingPart = false)
  }

  private def initialState(boundary: Boundary): State =
    State(boundary, FormState.fromBoundary(boundary), None, immutable.Queue.empty, inNonStreamingPart = false)
}
