package zhttp.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest

import java.net.{InetAddress, InetSocketAddress}

trait HttpConvertor[-X, +A] {
  type Ctx = ChannelHandlerContext

  def convert(request: X, ctx: Ctx): A
}

object HttpConvertor {
  implicit object JReqToRequest extends HttpConvertor[FullHttpRequest, Request] {
    override def convert(jReq: FullHttpRequest, ctx: Ctx): Request = {
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def headers: Headers = Headers.make(jReq.headers())

        override def remoteAddress: Option[InetAddress] = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress)
            case _                    => None
          }
        }

        override def data: HttpData = HttpData.fromByteBuf(jReq.content())
      }
    }
  }

  // not implicit
  private val empty: HttpConvertor[Any, Any] = new HttpConvertor[Any, Any] {
    override def convert(a: Any, ctx: Ctx): Any = a
  }

  implicit def identity[A]: HttpConvertor[A, A] =
    empty.asInstanceOf[HttpConvertor[A, A]] // Create empty once for performance.

}
