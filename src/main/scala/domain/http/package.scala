package zio-http.domain

import zio-http.domain.http.model.{Method, Path, Request, Response, URL}
import io.netty.util.CharsetUtil

import java.nio.charset.Charset

package object http {
  type HttpApp[-R, +E] = Http[R, E, Request, Response]
  type Endpoint        = (Method, URL)
  type Route           = (Method, Path)

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8
}
