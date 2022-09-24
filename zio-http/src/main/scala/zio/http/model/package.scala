package zio.http

import io.netty.util.CharsetUtil
import zio.http.model.headers._

import java.nio.charset.Charset
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object model {
  type Header = Headers.Header
  val Header: Headers.Header.type = Headers.Header

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8

  object HeaderNames extends HeaderNames

  object HeaderValues extends HeaderValues
}
