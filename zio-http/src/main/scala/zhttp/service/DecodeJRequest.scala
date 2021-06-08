package zhttp.service

import zhttp.core.JFullHttpRequest
import zhttp.http._

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest): Request[Any, Nothing, Nothing] = Request.FromJHttpRequest(jReq)
}
