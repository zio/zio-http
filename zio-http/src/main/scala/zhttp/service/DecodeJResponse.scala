package zhttp.service

import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): Client.ClientResponse = {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    Client.ClientResponse(status, headers, jRes.content())
  }
}
