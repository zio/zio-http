package zio.http.internal

import java.nio.charset.Charset

import zio.http.netty.NettyHeaderEncoding

private[http] trait HeaderEncoding {
  def getCharset(contentType: CharSequence, default: Charset): Charset
  def getMimeType(contentType: CharSequence): Option[CharSequence]
}

private[http] object HeaderEncoding {
  val default: HeaderEncoding = NettyHeaderEncoding
}
