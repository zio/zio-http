package zhttp.service

import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames, HttpVersion}
import zio.Task
trait EncodeClientParams {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encodeClientParams(jVersion: HttpVersion, req: Client.ClientRequest): Task[FullHttpRequest] =
    req.getBodyAsByteBuf.map { content =>
      val method  = req.method.asHttpMethod
      val url     = req.url
      val headers = req.getHeaders.encode

      val writerIndex = content.writerIndex()
      if (writerIndex != 0) {
        headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
      }
      // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
      val jReq        = new DefaultFullHttpRequest(jVersion, method, url, content)
      jReq.headers().set(headers)

      jReq
    }
}
