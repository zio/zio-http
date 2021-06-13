package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core.{JDefaultFullHttpRequest, JFullHttpRequest}
import zhttp.http._
trait EncodeRequest {

  /**
   * Converts Request to JFullHttpRequest
   */
  def encodeRequest(jVersion: JHttpVersion, req: Request[Any, Nothing, Any]): JFullHttpRequest = {
    val method      = req.method.asJHttpMethod
    val uri         = req.url.path match {
      case Root => "/"
      case path => path.asString
    }
    val content     = req match {
      case Request.Default(_, _, _, dContent) =>
        dContent match {
          case HttpData.CompleteContent(bytes) => JUnpooled.wrappedBuffer(bytes.toArray)
          case HttpData.BufferedContent(_)     => JUnpooled.EMPTY_BUFFER
          case HttpData.EmptyContent           => JUnpooled.EMPTY_BUFFER
        }
      case _                                  => JUnpooled.EMPTY_BUFFER
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
