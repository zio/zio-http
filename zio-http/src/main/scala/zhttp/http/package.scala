package zhttp

import io.netty.util.CharsetUtil

import java.nio.charset.Charset

package object http extends PathModule with RequestSyntax {
  type HttpApp[-R, +E]   = Http[R, E, Request, Response]
  type Endpoint          = (Method, URL)
  type Route             = (Method, Path)
  type SilentResponse[E] = CanBeSilenced[E, Response]

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8
}
