package zio.http.internal

import java.nio.charset.Charset

import zio.http.Body
import zio.http.model.HTTP_CHARSET
import zio.http.netty.NettyBody

trait BodyEncoding {
  def fromCharSequence(charSequence: CharSequence, charset: Charset = HTTP_CHARSET): Body
}

object BodyEncoding {
  val default: BodyEncoding = NettyBody
}
