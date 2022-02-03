package zhttp.http

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http.FullHttpRequest
import zio.Task

import java.net.InetAddress

trait HttpConvertor[X, +A] {
  def convert(request: X): Either[X,A]
}

object HttpConvertor {
  implicit object JReqToRequest extends HttpConvertor[FullHttpRequest, Request] {
    override def convert(jReq: FullHttpRequest): Either[FullHttpRequest,Request] = {
      Right(new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def getHeaders: Headers = Headers.make(jReq.headers())

        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())

        override def remoteAddress: Option[InetAddress] = ???
      }
      )
    }
  }

  implicit def identity[A]: HttpConvertor[A, A] = new HttpConvertor[A, A] {
    override def convert(a: A): Either[A, Nothing] = Left(a)
  }


}
