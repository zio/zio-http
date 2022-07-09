package zhttp

import io.netty.util.CharsetUtil
import zio.ZIO

import java.nio.charset.Charset

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {
  type HttpApp[-R, +E]                       = Http[R, E, Request, Response]
  type UHttpApp                              = HttpApp[Any, Nothing]
  type RHttpApp[-R]                          = HttpApp[R, Throwable]
  type UHttp[-A, +B]                         = Http[Any, Nothing, A, B]
  type ResponseZIO[-R, +E]                   = ZIO[R, E, Response]
  type Header                                = (CharSequence, CharSequence)
  type UMiddleware[+AIn, -BIn, -AOut, +BOut] = Middleware[Any, Nothing, AIn, BIn, AOut, BOut]

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8

  object HeaderNames  extends headers.HeaderNames
  object HeaderValues extends headers.HeaderValues
}
