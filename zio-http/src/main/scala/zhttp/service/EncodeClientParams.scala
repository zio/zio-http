package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames, HttpVersion}
import zhttp.http.HTTP_CHARSET
trait EncodeClientParams {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encodeClientParams(jVersion: HttpVersion, req: Client.ClientParams): FullHttpRequest = {
    val method          = req.method.asHttpMethod
    val url             = req.url
    val incomingHeaders = req.getHeaders
    val uri             = url.asString
    val content         = req.getBodyAsString match {
      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => Unpooled.EMPTY_BUFFER
    }

    val headers = incomingHeaders.encode

    if (!incomingHeaders.hasHeader(HttpHeaderNames.HOST)) {
      url.host match {
        case Some(value) => headers.set(HttpHeaderNames.HOST, value)
        case None        => headers.set(HttpHeaderNames.HOST, None)
      }
    }

    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
    val jReq        = new DefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }
}
