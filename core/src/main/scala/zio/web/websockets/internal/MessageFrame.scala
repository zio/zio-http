package zio.web.websockets.internal

import scala.util.control.NoStackTrace

import zio.Chunk

object UnexpectedError extends Exception with NoStackTrace

final case class MessageFrame private (opcode: Int, data: Chunk[Byte], last: Boolean)

object MessageFrame {

  def ping(data: Chunk[Byte] = Chunk.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(OpCode.Ping, data, true)
    else throw UnexpectedError

  def pong(data: Chunk[Byte] = Chunk.empty): MessageFrame =
    if (data.length <= 125) new MessageFrame(OpCode.Pong, data, true)
    else throw UnexpectedError

  def binary(data: Chunk[Byte], last: Boolean): MessageFrame =
    new MessageFrame(OpCode.Binary, data, last)

  def text(data: String, last: Boolean): MessageFrame =
    new MessageFrame(OpCode.Text, Chunk.fromArray(data.getBytes("UTF-8")), last)

  def close(code: CloseCode, description: String): MessageFrame = {
    val desc = description.getBytes("UTF-8")

    if (desc.length < 123)
      new MessageFrame(OpCode.Close, Chunk.fromArray(code.toBinary ++ desc), true)
    else
      throw UnexpectedError
  }

  def continuation(data: Chunk[Byte], last: Boolean): MessageFrame =
    new MessageFrame(OpCode.Continuation, data, last)
}
