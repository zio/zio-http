package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core.{JDefaultFullHttpRequest, JFullHttpRequest}
import zhttp.http._
trait EncodeRequest {

  /**
   * Converts Request to JFullHttpRequest
   */
  def encodeRequest(jVersion: JHttpVersion, req: Request[Any, Nothing, Complete]): JFullHttpRequest = {
    val method      = req.method.asJHttpMethod
    val uri         = req.url.path match {
      case Root => "/"
      case path => path.asString
    }
    val content     = req.content match {
      case Content.CompleteContent(bytes) => JUnpooled.wrappedBuffer(bytes.toArray)
      case _                              => JUnpooled.EMPTY_BUFFER
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
