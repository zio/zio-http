package zhttp

import io.netty.util.CharsetUtil
import zio.ZIO

import java.nio.charset.Charset

package object http extends PathModule with RequestSyntax {
  type Http[-R, +E]       = HttpChannel[R, E, Request, Response[R, E]]
  type RHttp[-R]          = Http[R, Throwable]
  type Endpoint           = (Method, URL)
  type Route              = (Method, Path)
  type SilentResponse[-E] = CanBeSilenced[E, UResponse]
  type UResponse          = Response[Any, Nothing]
  type UHttpResponse      = Response.HttpResponse[Any]
  type ResponseM[-R, +E]  = ZIO[R, E, Response[R, E]]
  type PartialRequest[+E] = CanSupportPartial[Request, E]

  object SilentResponse {
    def apply[E: SilentResponse]: SilentResponse[E] = implicitly[SilentResponse[E]]
  }

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8
}
