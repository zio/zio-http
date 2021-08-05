package zhttp.experiment

import io.netty.buffer.ByteBuf
import zio.Chunk
import io.netty.buffer.Unpooled

trait Codec[A] extends Codec.Encoder[A] with Codec.Decoder[A]

object Codec {

  trait Encoder[-A] {
    def encode(a: A): ByteBuf
  }

  object Encoder {
    implicit object ByteBufEncoder extends Encoder[ByteBuf] {
      override def encode(a: ByteBuf): ByteBuf = a
    }

    implicit object ChunkEncoder extends Encoder[Chunk[Byte]] {
      override def encode(a: Chunk[Byte]): ByteBuf = Unpooled.copiedBuffer(a.toArray)
    }
  }

  trait Decoder[+A] {
    def decode(byteBuf: ByteBuf): A
  }

  object Decoder {
    implicit object ByteBufDecoder extends Decoder[ByteBuf] {
      override def decode(byteBuf: ByteBuf): ByteBuf = byteBuf
    }

    implicit object ChunkDecoder extends Decoder[Chunk[Byte]] {
      override def decode(byteBuf: ByteBuf): Chunk[Byte] = Chunk.fromArray(byteBuf.array())
    }
  }
}
