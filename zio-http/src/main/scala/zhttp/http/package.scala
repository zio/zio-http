package zhttp

import io.netty.util.CharsetUtil
import zio.ZIO

import java.nio.charset.Charset

package object http extends PathModule with RequestSyntax with RouteDecoderModule {
  type HttpApp[-R, +E]        = Http[R, E, Request, Response]
  type UHttpApp               = HttpApp[Any, Nothing]
  type RHttpApp[-R]           = HttpApp[R, Throwable]
  type UHttp[-A, +B]          = Http[Any, Nothing, A, B]
  type SilentResponse[-E]     = CanBeSilenced[E, Response]
  type ResponseZIO[-R, +E]    = ZIO[R, E, Response]
  type Header                 = (CharSequence, CharSequence)
  type HttpMiddleware[-R, +E] = Middleware[R, E, Request, Response, Request, Response]
  type RequestP[+A]           = (Method, URL, Headers) => A

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8

  object SilentResponse {
    def apply[E: SilentResponse]: SilentResponse[E] = implicitly[SilentResponse[E]]
  }

  object HeaderNames  extends headers.HeaderNames
  object HeaderValues extends headers.HeaderValues
}
