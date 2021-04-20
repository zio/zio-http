package zhttp.service

import io.netty.buffer.ByteBufUtil
import zhttp.core.JFullHttpRequest
import zhttp.http._
import zio.Chunk

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromJHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = Chunk.fromArray(ByteBufUtil.getBytes(jReq.content()))
  } yield Request(endpoint, headers, HttpData.CompleteData(data))
}
