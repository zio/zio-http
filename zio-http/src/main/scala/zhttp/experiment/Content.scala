package zhttp.experiment

import zhttp.socket.SocketApp
import zio.stream.ZStream
import zio.Chunk
import java.nio.charset.Charset
import zhttp.http._

private[zhttp] sealed trait Content[-R, +E]

object Content {
  case object Empty                                                extends Content[Any, Nothing]
  final case class Text(text: String, charset: Charset)            extends Content[Any, Nothing]
  final case class Binary(text: Chunk[Byte])                       extends Content[Any, Nothing]
  final case class BinaryStream[R, E](stream: ZStream[R, E, Byte]) extends Content[R, E]
  final case class FromSocket[R, E](app: SocketApp[R, E])          extends Content[R, E]

  def empty: Content[Any, Nothing]                                               = Empty
  def text(data: String, charset: Charset = HTTP_CHARSET): Content[Any, Nothing] = Text(data, charset)
  def binary(data: Chunk[Byte]): Content[Any, Nothing]                           = Binary(data)
  def fromStream[R, E](stream: ZStream[R, E, Byte]): Content[R, E]               = BinaryStream(stream)
  def fromSocket[R, E](socketApp: SocketApp[R, E]): Content[R, E]                = FromSocket(socketApp)
}
