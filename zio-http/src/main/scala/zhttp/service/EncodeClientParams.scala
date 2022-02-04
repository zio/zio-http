package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames}
import zhttp.http.HTTP_CHARSET
trait EncodeClientParams {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encodeClientParams(req: Client.ClientRequest): FullHttpRequest = {
    val httpVersion = req.httpVersion
    val method      = req.method.asHttpMethod
    val url         = req.url

    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val path = url.relative.encode

    val content = req.getBodyAsString match {
      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => Unpooled.EMPTY_BUFFER
    }

    val encodedReqHeaders = req.getHeaders.encode

    val headers = url.host match {
      case Some(value) => encodedReqHeaders.set(HttpHeaderNames.HOST, value)
      case None        => encodedReqHeaders
    }

    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
    val jReq        = new DefaultFullHttpRequest(httpVersion, method, path, content)
    jReq.headers().set(headers)

    jReq
  }
}
