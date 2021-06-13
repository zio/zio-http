package zhttp

import io.netty.util.CharsetUtil
import zio.ZIO

import java.nio.charset.Charset

package object http extends PathModule with RequestSyntax {
  type UHttpApp              = HttpApp[Any, Nothing]
  type RHttpApp[-R]          = HttpApp[R, Throwable]
  type Endpoint              = (Method, URL)
  type Route                 = (Method, Path)
  type SilentResponse[-E]    = CanBeSilenced[E, Response[Any, Nothing, Complete]]
  type URequest              = Request[Any, Nothing, Any]
  type UResponse             = Response[Any, Nothing, Any]
  type CompleteResponse      = Response[Any, Nothing, Complete]
  type ResponseM[-R, +E, +B] = ZIO[R, E, Response[R, E, B]]
  type Complete
  type Buffered
  type Opaque

  object SilentResponse {
    def apply[E: SilentResponse]: SilentResponse[E] = implicitly[SilentResponse[E]]
  }

  /**
   * Default HTTP Charset
   */
  val HTTP_CHARSET: Charset = CharsetUtil.UTF_8
}
