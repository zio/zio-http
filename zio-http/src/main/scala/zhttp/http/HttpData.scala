package zhttp.http

import zhttp.core.{Direction, HBuf, HBuf1, Nat}
import zio.stream.ZStream
import zio.{Chunk, ZQueue}
import zio.blocking.Blocking
import zio.stm.TQueue

import java.nio.file.{Path => JPath}

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  private type BufferOut = HBuf1[Direction.Out]
  private type BufferIn  = HBuf1[Direction.In]

  case object Empty                                                                 extends HttpData[Any, Nothing]
  final case class CompleteData(data: BufferOut)                                    extends HttpData[Any, Nothing]
  final case class StreamData[R, E](stream: ZStream[R, E, BufferOut])               extends HttpData[R, E]
  final case class HBufQueue[R, E](queue: ZQueue[R, R, E, E, BufferOut, BufferOut]) extends HttpData[R, E]
  final case class HBufTQueue(queue: TQueue[BufferIn])                              extends HttpData[Any, Nothing]

  def apply(text: String): HttpData[Any, Nothing] = HttpData.fromString(text)

  /**
   * Helper to create CompleteData from ByteBuf
   */
  def fromBuf(buf: BufferOut): HttpData[Any, Nothing] = HttpData.CompleteData(buf)

  /**
   * Helper to create StreamData from Stream of Chunks
   */
  def fromStream[R, E](data: ZStream[R, E, HBuf[Nat.One, Direction.Out]]): HttpData[R, E] = HttpData.StreamData(data)

  /**
   * Creates HttpData from a stream of bytes
   */
  def fromByteStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] =
    fromStream(data.mapChunks(chunks => Chunk(HBuf.fromChunk(chunks))))

  /**
   * Creates HttpData by reading a file
   */
  def fromFile(path: JPath): HttpData[Blocking, Throwable] =
    HttpData.fromStream(ZStream.fromFile(path).mapChunks(chunks => Chunk(HBuf.fromChunk(chunks))))

  def fromQueue[R, E](queue: ZQueue[R, R, E, E, BufferOut, BufferOut]): HttpData[R, E] = HttpData.HBufQueue(queue)
  def fromQueueIn(queue: TQueue[BufferIn]): HttpData[Any, Nothing]                     = HBufTQueue(queue)

  /**
   * Helper to create Empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty

  /**
   * Creates HttpData from string
   */
  def fromString(text: String): HttpData[Any, Nothing] =
    HttpData.CompleteData(HBuf.fromString(text, HTTP_CHARSET))

  sealed trait DispatchData[A, D]
  object DispatchData {
    implicit object Outgoing extends DispatchData[Nat.One, Direction.Out]
    implicit object Incoming extends DispatchData[Nat.Two, Direction.In]
  }

}
