package zio.web

import zio.Chunk
import zio.web.codec.{ Codec, JsonCodec }

package object http extends HttpProtocol {

  val defaultProtocol: Codec = JsonCodec

  val allProtocols: Map[String, Codec] = Map("application/json" -> JsonCodec)

  private[http] val CR: Byte          = 13
  private[http] val LF: Byte          = 10
  private[http] val CRLF: Chunk[Byte] = Chunk(CR, LF)

  private[http] val NEW_LINE: Chunk[Byte]   = CRLF
  private[http] val EMPTY_LINE: Chunk[Byte] = NEW_LINE ++ NEW_LINE
}
