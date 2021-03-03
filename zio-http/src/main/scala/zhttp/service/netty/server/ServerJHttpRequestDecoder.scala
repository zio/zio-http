package zhttp.service.netty.server

import zhttp.core.netty.JFullHttpRequest
import zhttp.domain.http._
import zhttp.domain.http.model._

trait ServerJHttpRequestDecoder {

  /**
   * Tries to decode the [[io.netty.handler.codec.http.FullHttpRequest]] to [[Request]].
   */
  def unsafelyDecodeJFullHttpRequest(jReq: JFullHttpRequest): Request = {
    val url      = URL(Path(jReq.uri()))
    val method   = Method.fromJHttpMethod(jReq.method())
    val headers  = Header.make(jReq.headers())
    val endpoint = method -> url
    val data     = Request.Data(headers, HttpContent.Complete(jReq.content().toString(HTTP_CHARSET)))
    Request(endpoint, data)
  }
}
