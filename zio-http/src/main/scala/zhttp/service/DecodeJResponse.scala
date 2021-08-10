package zhttp.service

import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._

import scala.util.Try

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): Either[Throwable, UHttpResponse] = Try {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = HttpData.fromByteBuf(jRes.content())

    Response.http(status, headers, content): UHttpResponse
  }.toEither
}
