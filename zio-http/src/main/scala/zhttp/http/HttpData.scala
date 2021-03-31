package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zio.Chunk
import zio.blocking.Blocking
import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpData[-R, +E] extends Product with Serializable

object HttpData {
  case object Empty                                            extends HttpData[Any, Nothing]
  final case class CompleteData(data: Chunk[Byte])             extends HttpData[Any, Nothing]
  final case class StreamData[R, E](data: ZStream[R, E, Byte]) extends HttpData[R, E]

  sealed trait FormDataContent extends Product with Serializable

  final case class FileData(name: String, contentType: String, content: ZStream[Blocking, Throwable, Byte])
      extends FormDataContent
  final case class AttributeData(name: String, content: ZStream[Blocking, Throwable, Byte]) extends FormDataContent

  object MultipartFormData {
    def empty: MultipartFormData = MultipartFormData(Map.empty, Map.empty)
  }

  final case class MultipartFormData(
    attributes: Map[String, AttributeData],
    files: Map[String, FileData],
  ) extends HttpData[Any, Nothing]

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
