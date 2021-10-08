package zhttp.http

import io.netty.buffer.ByteBuf
import zio.Chunk
import zio.stream.ZStream

import java.nio.charset.Charset

sealed trait RequestContent[-R, +E]
object RequestContent {

  final case class BinaryStream[R, E](data: ZStream[R, E, Byte]) extends RequestContent[R, E]
  def fromStream[R, E](data: ZStream[R, E, Byte]): RequestContent[R, E] = BinaryStream(data)

  final case class Binary(data: Chunk[Byte]) extends RequestContent[Any, Nothing]
  def fromChunk(data: Chunk[Byte]): RequestContent[Any, Nothing] = Binary(data)

  final case class BinaryN(data: ByteBuf) extends RequestContent[Any, Nothing]
  def fromByteBuf(byteBuf: ByteBuf): RequestContent[Any, Nothing] = BinaryN(byteBuf)

  final case object Empty extends RequestContent[Any, Nothing]
  def empty: RequestContent[Any, Nothing] = Empty

  final case class Text(text: String, charset: Charset) extends RequestContent[Any, Nothing]
  def fromText(data: String, charset: Charset = HTTP_CHARSET): RequestContent[Any, Nothing] = Text(data, charset)

  def provide[R, E](r: R, content: RequestContent[R, E]): RequestContent[Any, E] = content match {
    case BinaryStream(data)  => BinaryStream(data.provide(r))
    case Binary(data)        => Binary(data)
    case BinaryN(data)       => BinaryN(data)
    case Empty               => Empty
    case Text(text, charset) => Text(text, charset)
  }
}
