package zhttp.service

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._
import zio.Chunk

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): Client.ClientResponse = {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = Chunk.fromArray(ByteBufUtil.getBytes(jRes.content()))
    jRes.release(jRes.refCnt())
    Client.ClientResponse(status, headers, content)
  }
}
