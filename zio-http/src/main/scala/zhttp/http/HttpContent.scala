package zhttp.http

import io.netty.buffer.ByteBuf
import zio.Chunk
import zio.stream.ZStream

/**
 * Content holder for Requests and Responses
 */
sealed trait HttpContent[-R, +A] extends Product with Serializable

object HttpContent {
  final case class Complete[A](data: Chunk[A])                        extends HttpContent[Any, A]
  final case class Chunked[R, A](data: ZStream[R, Nothing, Chunk[A]]) extends HttpContent[R, A]

  def complete(byteBuf: ByteBuf): Complete[Byte] = {
    val bytes = new Array[Byte](byteBuf.readableBytes)
    byteBuf.readBytes(bytes)
    HttpContent.Complete(Chunk.fromArray(bytes))
  }
}
