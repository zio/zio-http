package zhttp.service

import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http._
import zio.{Task, TaskManaged}

trait DecodeJResponse {

  /**
   * Tries to decode netty request into ZIO Http Request
   */
  def decodeJResponse(jRes: FullHttpResponse): TaskManaged[Client.ClientResponse] = {
    val status  = Status.fromHttpResponseStatus(jRes.status())
    val headers = Header.parse(jRes.headers())

    Task(Client.ClientResponse(status, headers, jRes.content())).toManaged(_.close().orDie)
  }
}
