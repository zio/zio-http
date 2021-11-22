package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{
  DefaultFullHttpRequest,
  FullHttpRequest,
  HttpHeaderNames,
  HttpHeaderValues,
  HttpVersion,
}
import io.netty.handler.codec.http2.HttpConversionUtil
import zhttp.http.URL.Location
import zhttp.http.{HTTP_CHARSET, Header, Path}
trait EncodeClientParams {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encodeClientParams(
    jVersion: HttpVersion,
    req: Client.ClientParams,
    enableHttp2: Boolean = false,
    streamId: Int = 1,
  ): FullHttpRequest = {
    val method      = req.method.asHttpMethod
    val uri         = req.url.path match {
      case Path.End => "/"
      case _        => req.url.relative.asString
    }
    val content     = req.getBodyAsString match {
      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => Unpooled.EMPTY_BUFFER
    }
    val headers     = Header.disassemble(req.getHeaders)
    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    val scheme      = req.url.kind match {
      case Location.Relative               => ""
      case Location.Absolute(scheme, _, _) => scheme.asString
    }
    val jReq        = new DefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)
    if (enableHttp2) {
      jReq
        .headers()
        .set(HttpConversionUtil.ExtensionHeaderNames.STREAM_ID.text(), streamId)
        .add(HttpConversionUtil.ExtensionHeaderNames.SCHEME.text(), scheme)
        .add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
        .add(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.DEFLATE)
    }

    jReq
  }
}
