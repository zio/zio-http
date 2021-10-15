package zhttp.service

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http._

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: FullHttpRequest, ctx: ChannelHandlerContext): Either[HttpError, Client.ClientParams] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = HttpAttribute.fromByteBuf(jReq.content())
  } yield Client.ClientParams(endpoint, headers, data, ctx)

}
