package zhttp.service

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
    bytes    = new Array[Byte](jReq.content().readableBytes)
    _        = jReq.content().duplicate.readBytes(bytes)
    data     = Request.Data(
      headers,
      HttpContent.Complete(Chunk.fromArray(bytes)),
    )
  } yield Request(endpoint, data)
}
