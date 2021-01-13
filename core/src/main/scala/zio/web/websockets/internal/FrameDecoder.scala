package zio.web.websockets.internal

import java.nio.ByteBuffer

import scala.annotation.switch

import zio.{ Chunk, ChunkBuilder, Has, Task, UIO, ULayer, ZLayer }

object FrameDecoder {

  trait Service {
    def decode(buffer: ByteBuffer, masked: Boolean): Task[MessageFrame]
  }

  val live: ULayer[Has[FrameDecoder.Service]] =
    ZLayer.succeed {
      new FrameDecoder.Service {

        override def decode(buffer: ByteBuffer, masked: Boolean): Task[MessageFrame] = {
          val chunk = Chunk.fromArray(buffer.array())

          if (chunk.length < 2) Task.fail(new Exception("Malformed frame found"))
          else {
            val fin    = (chunk(0) >> 7) == 0x0
            val opcode = chunk(0) & 0x0F

            for {
              len                     <- getLength(chunk, masked)
              (headerLen, payloadLen) = len
              payload                 <- getPayload(headerLen, payloadLen, chunk, masked)
            } yield (opcode: @switch) match {
              case CONTINUATION => MessageFrame.continuation(payload, fin)
              case TEXT         => MessageFrame.string(new String(payload.toArray, "UTF-8"), fin)
              case BINARY       => MessageFrame.binary(payload, fin)
              case CLOSE        => getCloseFrame(payload)
              case PING         => MessageFrame.ping(payload)
              case PONG         => MessageFrame.pong(payload)
            }
          }
        }

        private def getLength(chunk: Chunk[Byte], masked: Boolean) = {
          val lengths = UIO.succeed {
            val octetLen = chunk(1) & MASK

            if (octetLen == 0x7E) {
              (4, (chunk(2) << 8) + chunk(3))
            } else if (octetLen == 0x7F) {
              (
                10,
                (chunk(2) << 56) +
                  (chunk(3) << 48) +
                  (chunk(4) << 40) +
                  (chunk(5) << 32) +
                  (chunk(6) << 24) +
                  (chunk(7) << 16) +
                  (chunk(8) << 8) +
                  chunk(9)
              )
            } else {
              (2, octetLen)
            }
          }

          lengths.flatMap {
            case (headerLen, payloadLen) =>
              //TODO: come up with sensible errors
              if ((headerLen + payloadLen) != chunk.length) Task.fail(new Exception(""))
              else if (masked) Task.succeed((headerLen + 4, payloadLen))
              else Task.succeed((headerLen, payloadLen))
          }
        }

        private def getPayload(headerLength: Int, payloadLength: Int, data: Chunk[Byte], masked: Boolean) =
          Task.effect {
            val payload = ChunkBuilder.make[Byte](payloadLength)

            var i = headerLength

            while (i < payloadLength) {
              if (masked)
                payload.addOne((data(i) ^ data(headerLength - 4 + (i % 4))).toByte)
              else
                payload.addOne(data(i))

              i += 1
            }

            payload.result()
          }

        private def getCloseFrame(payload: Chunk[Byte]) = {
          val code   = (payload(0) << 8) + (payload(1) & 0xFF)
          val reason = new String(payload.drop(2).toArray, "UTF-8")

          //TODO: Fail if code is out of close code's range
          MessageFrame.close(CloseCode.fromInt(code).get, reason)
        }
      }
    }
}
