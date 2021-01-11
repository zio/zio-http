package zio.web.websockets.internal

import java.nio.ByteBuffer

import scala.annotation.switch

import zio.{ Has, Task, ULayer, ZLayer }
import zio.ChunkBuilder

object FrameDecoder {

  trait Service {
    def decode(buffer: ByteBuffer, masked: Boolean): Task[MessageFrame]
  }

  val live: ULayer[Has[FrameDecoder.Service]] =
    ZLayer.succeed {
      new FrameDecoder.Service {
        override def decode(buffer: ByteBuffer, masked: Boolean): zio.Task[MessageFrame] =
          new Live(masked).decode(buffer)

      }

    }

  final private[this] class Live(masked: Boolean) {

    def decode(buffer: ByteBuffer): Task[MessageFrame] =
      // WIP
      Task.effect(getFrame(4, 124, buffer.array()))

    private def getFrame(headerLength: Int, payloadLength: Int, data: Array[Byte]) = {
      val fin     = (data(0) >> 7) == 0x1
      val opcode  = data(0) & 0x0F
      val payload = ChunkBuilder.make[Byte](payloadLength)

      var i = headerLength

      while (i < payloadLength) {
        if (masked)
          payload.addOne((data(i) ^ data(headerLength - 4 + (i % 4))).toByte)
        else
          payload.addOne(data(i))

        i += 1
      }

      (opcode: @switch) match {
        case CONTINUATION => MessageFrame.continuation(payload.result(), fin)
        case TEXT         => MessageFrame.string(new String(payload.result().toArray, "UTF-8"), fin)
        case BINARY       => MessageFrame.binary(payload.result(), fin)
        case CLOSE        => MessageFrame.close(CloseCode.NormalClosure, "")
        case PING         => MessageFrame.ping(payload.result())
        case PONG         => MessageFrame.pong(payload.result())
      }

    }

  }
}
