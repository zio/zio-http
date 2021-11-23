package zhttp.service

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http._

import java.net.InetSocketAddress

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: FullHttpRequest, ctx: ChannelHandlerContext): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method        = Method.fromHttpMethod(jReq.method())
    headers       = Header.make(jReq.headers())
    data          = HttpData.fromByteBuf(jReq.content())
    remoteAddress =
      if (ctx != null && ctx.channel().remoteAddress().isInstanceOf[InetSocketAddress])
        Some(ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
      else
        None
  } yield Request(method, url, headers, remoteAddress, data)

}
