package zio.web

import zio.Chunk

package object http extends HttpProtocolModule {
  val defaultProtocol: codec.Codec = codec.JsonCodec

  val allProtocols: Map[String, codec.Codec] = Map("application/json" -> codec.JsonCodec)

  private[http] val CR: Byte          = 13
  private[http] val LF: Byte          = 10
  private[http] val CRLF: Chunk[Byte] = Chunk(CR, LF)

  private[http] val NEW_LINE: Chunk[Byte]   = CRLF
  private[http] val EMPTY_LINE: Chunk[Byte] = NEW_LINE ++ NEW_LINE
}
