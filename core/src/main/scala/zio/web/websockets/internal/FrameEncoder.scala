package zio.web.websockets.internal

import zio._
import zio.random.Random

object FrameEncoder {

  trait Service {
    def encode(frame: MessageFrame, masked: Boolean): Task[Chunk[Byte]]
  }

  val live: URLayer[Has[MaskingKey.Service], Has[FrameEncoder.Service]] =
    ZLayer.fromService { masking =>
      new FrameEncoder.Service {

        override def encode(frame: MessageFrame, masked: Boolean): Task[Chunk[Byte]] =
          // we don't need to generate maskingKey when sending data from a server..
          masking.get.flatMap { maskingKey =>
            Task.effect {
              val fin  = if (frame.last) 0x80 else 0x00
              val mask = if (masked) 0x80 else 0x00
              val len  = frame.data.length.toLong

              val buf = ChunkBuilder.make[Byte]()

              buf.addOne((fin + frame.frameType.opcode).toByte)
              buf.addAll(encodeLength(len, mask))
              buf.addAll(encodeData(frame.data, masked, maskingKey))
              buf.result()
            }
          }

        private def encodeLength(len: Long, mask: Int) =
          if (len < 126) {
            Chunk((len + mask).toByte)
          } else if (len < 65536) {
            Chunk(
              (0x7E + mask).toByte,
              (len >> 8).toByte,
              (len & 0xFF).toByte
            )
          } else {
            Chunk(
              (0x7F + mask).toByte,
              (len >> 56).toByte,
              ((len >> 48) & 0xFF).toByte,
              ((len >> 40) & 0xFF).toByte,
              ((len >> 32) & 0xFF).toByte,
              ((len >> 24) & 0xFF).toByte,
              ((len >> 16) & 0xFF).toByte,
              ((len >> 8) & 0xFF).toByte,
              (len & 0xFF).toByte
            )
          }

        private def encodeData(data: Chunk[Byte], masked: Boolean, maskingKey: Chunk[Byte]) =
          if (masked) {
            val buf = ChunkBuilder.make[Byte]()

            var i = 0

            while (i < data.length) {
              buf.addOne((data(i) ^ maskingKey(i & 0x3)).toByte)
              i += 1
            }

            maskingKey ++ buf.result()
          } else data
      }
    }

  val default: ULayer[Has[Service]] =
    Random.live >>> MaskingKey.live >>> live

  def encode(frame: MessageFrame, masked: Boolean): ZIO[Has[FrameEncoder.Service], Throwable, Chunk[Byte]] =
    ZIO.accessM(_.get[FrameEncoder.Service].encode(frame, masked))
}
