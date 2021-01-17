package zio.web.websockets.internal

import scala.annotation.switch

import zio.{ Chunk, ChunkBuilder, Has, Task, ULayer, ZIO, ZLayer }

object FrameDecoder {

  trait Service {
    def decode(chunk: Chunk[Byte], masked: Boolean): Task[MessageFrame]
  }

  val live: ULayer[Has[FrameDecoder.Service]] =
    ZLayer.succeed {
      new FrameDecoder.Service {

        override def decode(chunk: Chunk[Byte], masked: Boolean): Task[MessageFrame] =
          if (chunk.length < 2) Task.fail(new Exception("Malformed frame found"))
          else
            Task.effect {
              val fin    = (chunk(0) >> 7) != 0x0
              val opcode = chunk(0) & 0x0F

              val (headerLen, payloadLen) = getLength(chunk, masked: Boolean)
              val data                    = readData(headerLen, payloadLen, chunk, masked)

              (opcode: @switch) match {
                case OpCode.Continuation => MessageFrame.continuation(data, fin)
                case OpCode.Text         => MessageFrame.text(new String(data.toArray, "UTF-8"), fin)
                case OpCode.Binary       => MessageFrame.binary(data, fin)
                case OpCode.Close        => getCloseFrame(data)
                case OpCode.Ping         => MessageFrame.ping(data)
                case OpCode.Pong         => MessageFrame.pong(data)
              }
            }

        private def getLength(chunk: Chunk[Byte], masked: Boolean) = {
          val octetLen = chunk(1) & 0x7F //0111 1111

          val (headerLen, payloadLen) =
            if (octetLen == 0x7E) {
              (4, (chunk(2) << 8 & 0xFF00) | (chunk(3) & 0x00FF))
            } else if (octetLen == 0x7F) {
              val longLen = getLong(chunk)

              if (longLen > Int.MaxValue) throw UnexpectedError
              else (10, longLen.toInt)
            } else {
              (2, octetLen)
            }

          if (masked) (headerLen + 4, payloadLen)
          else (headerLen, payloadLen)
        }

        private def getLong(chunk: Chunk[Byte]): Long = {
          val a = ((chunk(2) & 0xFF) << 56).toLong
          val b = ((chunk(3) & 0xFF) << 48).toLong
          val c = ((chunk(4) & 0xFF) << 40).toLong
          val d = ((chunk(5) & 0xFF) << 32).toLong
          val e = ((chunk(6) & 0xFF) << 24)
          val f = ((chunk(7) & 0xFF) << 16)
          val g = ((chunk(8) & 0xFF) << 8)
          val h = chunk(9) & 0xFF.toLong

          a | b | c | d | e | f | g | h
        }

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

          //TODO: Fail if code is out of close code's range
          MessageFrame.close(CloseCode.fromInt(code).get, reason)
        }
      }
    }

  def decode(chunk: Chunk[Byte], masked: Boolean): ZIO[Has[FrameDecoder.Service], Throwable, MessageFrame] =
    ZIO.accessM(_.get[Service].decode(chunk, masked))
}
