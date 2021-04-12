package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zio.stream.{ZStream, ZTransducer}
import zio.{Chunk, UIO, ZIO}

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  case object Empty                                extends HttpData[Any, Nothing]
  final case class CompleteData(data: Chunk[Byte]) extends HttpData[Any, Nothing] {
    def asString: ZIO[Any, Nothing, String] = UIO.succeed(data.map(_.toChar).mkString)
  }

  final case class StreamData[R, E](data: ZStream[R, E, Byte]) extends HttpData[R, E] {
    def asString: ZIO[R, E, String] = data.aggregate(ZTransducer.utf8Decode).fold("")(_ + _)
  }

  /**
   * Helper to create CompleteData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData[Any, Nothing] = {
    HttpData.CompleteData(Chunk.fromArray(ByteBufUtil.getBytes(byteBuf)))
  }

  /**
   * Helper to create StreamData from Stream of Chunks
   */
  def fromStream[R, E](data: ZStream[R, E, Byte]): HttpData[R, E] = HttpData.StreamData(data)

  /**
   * Helper to create Empty HttpData
   */
  def empty: HttpData[Any, Nothing] = Empty
}
