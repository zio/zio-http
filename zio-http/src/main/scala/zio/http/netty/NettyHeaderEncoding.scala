package zio.http.netty

import java.nio.charset.Charset

import zio.http.internal.HeaderEncoding

import io.netty.handler.codec.http.HttpUtil

private[http] object NettyHeaderEncoding extends HeaderEncoding {

  override final def getCharset(contentType: CharSequence, default: Charset): Charset =
    HttpUtil.getCharset(contentType, default)

  override final def getMimeType(contentType: CharSequence): Option[CharSequence] =
    Option(HttpUtil.getMimeType(contentType))
}
