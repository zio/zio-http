package zio.web.websockets.internal

import java.nio.charset.StandardCharsets

import scala.annotation.switch

import zio._

object FrameDecoder {

  trait Service {
    def decode(chunk: Chunk[Byte], masked: Boolean): IO[FrameError, MessageFrame]
  }

  val live: ULayer[Has[FrameDecoder.Service]] =
    ZLayer.succeed {
      new FrameDecoder.Service {

        override def decode(chunk: Chunk[Byte], masked: Boolean): IO[FrameError, MessageFrame] =
          if (chunk.length < 2) IO.fail(FrameError.MalformedFrame("Frame doesn't contain required header's fields"))
          else {
            val fin    = (chunk(0) >> 7) != 0x0
            val opcode = chunk(0) & 0x0F

            payloadLength(chunk).flatMap { payloadLen =>
              val headerLen = headerLength(chunk(1), masked)
              val data      = readData(headerLen, payloadLen, chunk, masked)

              (opcode: @switch) match {
                case OpCode.Continuation => IO.succeed(MessageFrame.continuation(data, fin))
                case OpCode.Text         => IO.succeed(MessageFrame.text(new String(data.toArray, StandardCharsets.UTF_8), fin))
                case OpCode.Binary       => IO.succeed(MessageFrame.binary(data, fin))
                case OpCode.Close        => getCloseFrame(data)
                case OpCode.Ping         => IO.succeed(MessageFrame.ping(data))
                case OpCode.Pong         => IO.succeed(MessageFrame.pong(data))
              }
            }
          }

        private def headerLength(b: Byte, masked: Boolean) = {
          val len = (b & 0x7F: @switch) match {
            case 0x7E => 4
            case 0x7F => 10
            case _    => 2
          }

          if (masked) len + 4 else len
        }

        private def payloadLength(chunk: Chunk[Byte]) = {
          val octetLen = chunk(1) & 0x7F

          (octetLen: @switch) match {
            case 0x7E =>
              IO.succeed((chunk(2) << 8 & 0xFF00) | (chunk(3) & 0x00FF))
            case 0x7F =>
              IO.succeed(getLong(chunk)).flatMap { len =>
                if (len > Int.MaxValue)
                  IO.fail(FrameError.TooBigData("The length of payload is too big"))
                else
                  IO.succeed(len.toInt)
              }
            case _ =>
              IO.succeed(octetLen)
          }
        }

        private def getLong(chunk: Chunk[Byte]): Long =
          ((chunk(2) & 0xFF) << 56).toLong |
            ((chunk(3) & 0xFF) << 48).toLong |
            ((chunk(4) & 0xFF) << 40).toLong |
            ((chunk(5) & 0xFF) << 32).toLong |
            ((chunk(6) & 0xFF) << 24) |
            ((chunk(7) & 0xFF) << 16) |
            ((chunk(8) & 0xFF) << 8) |
            chunk(9) & 0xFF

        private def readData(idx: Int, dataLength: Int, data: Chunk[Byte], masked: Boolean) = {
          val chunk = data.slice(idx, data.length)

          if (masked) {
            val payload    = ChunkBuilder.make[Byte](dataLength)
            val maskingKey = data.slice(idx - 4, (idx - 4) + 4)

            var i = 0

            while (i < dataLength) {
              payload.addOne((chunk(i) ^ maskingKey(i & 0x3)).toByte)

              i += 1
            }

            payload.result()
          } else chunk
        }

        private def getCloseFrame(payload: Chunk[Byte]) = {
          val code   = (payload(0) << 8 & 0xFF00) | (payload(1) & 0x00FF)
          val reason = new String(payload.drop(2).toArray, "UTF-8")

          CloseCode
            .fromInt(code)
            .fold[IO[FrameError, MessageFrame]](
              IO.fail(FrameError.WrongCode(s"The ${code} is out of close code range"))
            )(c => IO.succeed(MessageFrame.close(c, reason)))
        }
      }
    }

  def decode(chunk: Chunk[Byte], masked: Boolean): ZIO[Has[FrameDecoder.Service], FrameError, MessageFrame] =
    ZIO.accessM(_.get[Service].decode(chunk, masked))
}
