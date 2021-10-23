package zhttp.experiment

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.handler.codec.http.HttpContent
import zhttp.http.HTTP_CHARSET
import zio.Chunk

trait ConvertibleToChunkByte[A] {
  def toChunkByte(a: A): Chunk[Byte]
}
object ConvertibleToChunkByte   {
  implicit object StrToChunkByte         extends ConvertibleToChunkByte[String]                   {
    override def toChunkByte(a: String): Chunk[Byte] = Chunk.fromArray(a.getBytes(HTTP_CHARSET))
  }
  implicit object ByteBufToChunkByte     extends ConvertibleToChunkByte[ByteBuf]                  {
    override def toChunkByte(a: ByteBuf): Chunk[Byte] = Chunk.fromArray(ByteBufUtil.getBytes(a))
  }
  implicit object HttpContentToChunkByte extends ConvertibleToChunkByte[HttpContent]              {
    override def toChunkByte(a: HttpContent): Chunk[Byte] = Chunk.fromArray(ByteBufUtil.getBytes(a.content()))
  }
  implicit object HttpMessageToChunkByte extends ConvertibleToChunkByte[HttpMessage[Chunk[Byte]]] {
    override def toChunkByte(a: HttpMessage[Chunk[Byte]]): Chunk[Byte] = a.raw
  }
}
