package zio.http

import zio.http.model.Headers

import java.net._
import java.nio.charset.Charset

package object forms {
  private[zio] val `UTF-8` = Charset.forName("UTF-8")

  private[forms] val CRLF = "\r\n"

}
