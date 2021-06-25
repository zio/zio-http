package zhttp.service

import io.netty.handler.codec.http2.Http2HeadersFrame
import zhttp.core.JFullHttpRequest
import zhttp.http._

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromJHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = HttpData.fromByteBuf(jReq.content())
  } yield Request(endpoint, headers, data)
  def decodeHttp2Header(hh: Http2HeadersFrame):Either[HttpError, Request]= for {
    url<- URL.fromString(hh.headers().path().toString)
    method= Method.fromString(hh.headers().method().toString)
    endpoint = method -> url
  } yield Request(endpoint)
}
