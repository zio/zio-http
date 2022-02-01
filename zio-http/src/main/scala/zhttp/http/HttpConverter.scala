package zhttp.http

import java.net.InetAddress

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpRequest
import zio.Task

trait HttpConverter[-X, +A] {
  def convert(request: X): A
}
object HttpConverter        {
  implicit object JReqToRequest extends HttpConverter[FullHttpRequest, Request] {
    override def convert(jReq: FullHttpRequest): Request = {
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def getHeaders: Headers = Headers.make(jReq.headers())

        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())

        override def remoteAddress: Option[InetAddress] = ???
      }
    }
  }
}
