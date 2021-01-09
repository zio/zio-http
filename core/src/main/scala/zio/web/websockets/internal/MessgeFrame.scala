package zio.web.websockets.internal

import scala.util.control.NoStackTrace

object UnexpectedError extends Exception with NoStackTrace

final private[websockets] case class MessageFrame private (frameType: FrameType, data: Array[Byte], last: Boolean) {
  self =>

  /**
   * Mask a payload
   * @param maskKey a 32-bit value chosen at random
   * @return the {{MessageFrame}} whose data is masked
   */
  def maskData(maskKey: String): MessageFrame =
    if (maskKey.length() == 4) {
      val currArray = new Array[Byte](data.length)
      var i         = 0

      while (i < currArray.length) {
        currArray.update(i, (self.data(i) ^ maskKey(i % 4)).toByte)
        i += 1
      }

      self.copy(data = currArray)
    } else throw UnexpectedError

}

private[websockets] object MessageFrame {

  def ping(data: Array[Byte] = Array.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(FrameType.Ping, data, true)
    else throw UnexpectedError

  def pong(data: Array[Byte] = Array.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(FrameType.Pong, data, true)
    else throw UnexpectedError

  def binary(data: Array[Byte], last: Boolean = true): MessageFrame =
    new MessageFrame(FrameType.Binary, data, last)

  def string(data: String, last: Boolean = true): MessageFrame =
    new MessageFrame(FrameType.Text, data.getBytes("UTF-8"), last)

  def close(code: CloseCode, description: String): MessageFrame = {
    val desc = description.getBytes("UTF-8")

    if (desc.length < 123)
      new MessageFrame(FrameType.Close, code.toBinary ++ desc, true)
    else
      throw UnexpectedError
  }

  def continuation(data: Array[Byte], last: Boolean): MessageFrame =
    new MessageFrame(FrameType.Continuation, data, last)
}

sealed abstract class FrameType(val opcode: Int)

object FrameType {
  case object Continuation extends FrameType(0x0)
  case object Text         extends FrameType(0x1)
  case object Binary       extends FrameType(0x2)
  case object Close        extends FrameType(0x8)
  case object Ping         extends FrameType(0x9)
  case object Pong         extends FrameType(0xa)
}
