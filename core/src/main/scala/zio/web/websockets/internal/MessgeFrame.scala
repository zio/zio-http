package zio.web.websockets.internal

import zio.Chunk

sealed abstract class FrameType(val opcode: Int)

object FrameType {
  case object Continuation extends FrameType(0x0)
  case object Text         extends FrameType(0x1)
  case object Binary       extends FrameType(0x2)
  case object Close        extends FrameType(0x8)
  case object Ping         extends FrameType(0x9)
  case object Pong         extends FrameType(0xa)
}

final case class MessageFrame private (frameType: FrameType, data: Chunk[Byte], last: Boolean)

object MessageFrame {

  def ping(data: Chunk[Byte] = Chunk.empty): Option[MessageFrame] =
    if (data.length <= 125) Some(new MessageFrame(FrameType.Ping, data, true))
    else None

  def pong(data: Chunk[Byte] = Chunk.empty): Option[MessageFrame] =
    if (data.length <= 125) Some(new MessageFrame(FrameType.Pong, data, true))
    else None

  def binary(data: Chunk[Byte], last: Boolean = true): MessageFrame =
    new MessageFrame(FrameType.Binary, data, last)

  def string(data: String, last: Boolean = true): MessageFrame =
    new MessageFrame(FrameType.Text, Chunk.fromArray(data.getBytes("UTF-8")), last)

  def close(code: CloseCode, description: String): Option[MessageFrame] = {
    val desc = description.getBytes("UTF-8")

    if (desc.length < 123)
      Some(new MessageFrame(FrameType.Close, Chunk.fromArray(code.toBinary ++ desc), true))
    else
      None
  }

  def continuation(data: Chunk[Byte], last: Boolean): MessageFrame =
    new MessageFrame(FrameType.Continuation, data, last)
}

sealed abstract class CloseCode(private val code: Int) {

  def toBinary: Array[Byte] = {
    val arr = new Array[Byte](2)
    arr.update(0, ((code >> 8) & 0xFF).toByte)
    arr.update(1, (code & 0xFF).toByte)
    arr
  }
}

object CloseCode {
  case object CloseConnection   extends CloseCode(1000)
  case object ServerUnavailable extends CloseCode(1001)
  case object ProtocolError     extends CloseCode(1002)
  case object UnknownDataType   extends CloseCode(1003)
  case object InconsistentData  extends CloseCode(1007)
  case object Unknown           extends CloseCode(1008)
  case object MessageTooBig     extends CloseCode(1009)
  case object NoExtensions      extends CloseCode(1010)
  case object Exception         extends CloseCode(1011)
  case object NoCode            extends CloseCode(1005)
}
