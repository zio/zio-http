package zhttp

import io.netty.util.CharsetUtil
import zio.ZIO

import java.nio.charset.Charset

package object http extends PathModule with RequestSyntax with HttpAppSyntax {
  type UHttp[-A, +B]      = Http[Any, Nothing, A, B]
  type HttpApp[-R, +E]    = Http[R, E, Request, Response[R, E]]
  type UHttpApp           = HttpApp[Any, Nothing]
  type RHttpApp[-R]       = HttpApp[R, Throwable]
  type Endpoint           = (Method, URL)
  type Route              = (Method, Path)
  type SilentResponse[-E] = CanBeSilenced[E, UResponse]
  type UResponse          = Response[Any, Nothing]
  type UHttpResponse      = Response.HttpResponse[Any, Nothing]
  type ResponseM[-R, +E]  = ZIO[R, E, Response[R, E]]

  object SilentResponse {
    def apply[E: SilentResponse]: SilentResponse[E] = implicitly[SilentResponse[E]]
  }

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8
}
