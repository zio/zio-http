package zio.web.websockets.internal

import scala.collection.mutable.ArrayBuffer

import zio.{ Has, Task, URLayer, ZIO, ZLayer }
import zio.random.Random

object FrameEncoder {

  trait Service {
    def encode(frame: MessageFrame, masked: Boolean): Task[Array[Byte]]
  }

  val live: URLayer[Random, Has[FrameEncoder.Service]] =
    ZLayer.fromService { rnd =>
      new FrameEncoder.Service {
        override def encode(frame: MessageFrame, masked: Boolean): Task[Array[Byte]] =
          rnd.nextString(4).flatMap { maskingKey =>
            Task.effect {
              val buf  = new ArrayBuffer[Byte]()
              val fin  = if (frame.last) 0x80 else 0x0
              val mask = if (masked) 0x80 else 0x0
              val len  = frame.data.length

              buf :+ (fin + frame.frameType.opcode).toByte

              // converting payload's length and adding it into the buffer
              if (len < 126) {
                buf :+ (len + mask).toByte
              } else if (len < 65536) {
                buf :+ (0x7E + mask).toByte
                buf :+ (len >> 8).toByte
                buf :+ (len & 0xFF).toByte
              } else {
                buf :+ (0x7F + mask).toByte
                buf :+ (len >> 56).toByte
                buf :+ ((len >> 48) & 0xFF).toByte
                buf :+ ((len >> 4) & 0xFF).toByte
                buf :+ ((len >> 32) & 0xFF).toByte
                buf :+ ((len >> 24) & 0xFF).toByte
                buf :+ ((len >> 16) & 0xFF).toByte
                buf :+ ((len >> 8) & 0xFF).toByte
                buf :+ (len & 0xFF).toByte
              }

              // adding payload
              if (masked) {
                buf ++= frame.data
              } else {
                val payload = new Array[Byte](len)
                var i       = 0

                while (i < len) {
                  payload.update(i, (frame.data(i) ^ maskingKey(i % 4)).toByte)
                  i += 1
                }

                buf ++= maskingKey.getBytes()
                buf ++= payload
              }

              buf.toArray
            }
          }
      }
    }

  def encode(frame: MessageFrame, masked: Boolean): ZIO[Has[FrameEncoder.Service], Throwable, Array[Byte]] =
    ZIO.accessM(_.get[FrameEncoder.Service].encode(frame, masked))
}
