package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): Client.ClientResponse = {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Headers.decode(jRes.headers())
    val content = Unpooled.copiedBuffer(jRes.content())
    jRes.release(jRes.refCnt())
    Client.ClientResponse(status, headers, content)
  }
}
