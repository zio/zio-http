package zhttp.service

import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._
import zio.Chunk

import scala.util.Try

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): Either[Throwable, Client.ClientResponse] = Try {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())
    val content = Chunk.fromArray(jRes.content().array())

    Client.ClientResponse(status, headers, content)
  }.toEither
}
