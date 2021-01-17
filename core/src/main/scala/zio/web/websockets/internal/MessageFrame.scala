package zio.web.websockets.internal

import java.nio.charset.StandardCharsets

import zio.Chunk

private[internal] object OpCode {
  final val Continuation = 0x00
  final val Text         = 0x01
  final val Binary       = 0x02
  final val Close        = 0x08
  final val Ping         = 0x09
  final val Pong         = 0x0A
}

sealed private[internal] trait FrameType {
  val opcode: Int
}
private[internal] object FrameType {

  case object Text extends FrameType {
    final val opcode = OpCode.Text
  }

  case object Binary extends FrameType {
    final val opcode = OpCode.Binary
  }

  case object Continuation extends FrameType {
    final val opcode = OpCode.Continuation
  }

  case object Ping extends FrameType {
    final val opcode = OpCode.Ping
  }

  case object Pong extends FrameType {
    final val opcode = OpCode.Pong
  }

  final case class Close(code: CloseCode, reason: String) extends FrameType {
    final val opcode = OpCode.Close
  }
}
// final case class MessageFrame(opcode: Int, data: Chunk[Byte], last: Boolean)
final case class MessageFrame private (last: Boolean, data: Chunk[Byte], frameType: FrameType)

object MessageFrame {

  def binary(data: Chunk[Byte], last: Boolean): MessageFrame =
    new MessageFrame(last, data, FrameType.Binary)

  def text(data: String, last: Boolean): MessageFrame =
    new MessageFrame(last, Chunk.fromArray(data.getBytes(StandardCharsets.UTF_8)), FrameType.Text)

  def continuation(data: Chunk[Byte], last: Boolean): MessageFrame =
    new MessageFrame(last, data, FrameType.Continuation)

  def ping(data: Chunk[Byte] = Chunk.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(true, data, FrameType.Ping)
    else throw FrameError.TooBigData("PING frame length must be less that 126 bytes")

  def pong(data: Chunk[Byte] = Chunk.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(true, data, FrameType.Pong)
    else throw FrameError.TooBigData("PONG frame length must be less that 126 bytes")

  def close(code: CloseCode, description: String): MessageFrame =
    if (description.length < 123) {
      val reason = Chunk.fromArray(description.getBytes("UTF-8"))
      new MessageFrame(true, code.toBinary ++ reason, FrameType.Close(code, description))
    } else throw FrameError.TooBigData("CLOSE frame's reason must be less than 123 bytes")
}
