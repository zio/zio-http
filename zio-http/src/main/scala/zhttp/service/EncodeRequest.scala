package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames}
import zhttp.core.{JDefaultFullHttpRequest, JFullHttpRequest, JHttpVersion}
import zhttp.http.{HTTP_CHARSET, Header, Request}
import zio.UIO
trait EncodeRequest {

  /**
   * Converts Request to JFullHttpRequest
   */
  def encodeRequest(jVersion: JHttpVersion, req: Request): UIO[JFullHttpRequest] = req.getBodyAsString.map { bodyText =>
    val method      = req.method.asJHttpMethod
    val uri         = req.url.asString
    val content     = bodyText match {
      case Some(text) => JUnpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => JUnpooled.EMPTY_BUFFER
    }
    val headers     = Header.disassemble(req.headers)
    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(JHttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    val jReq        = new JDefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }
}
