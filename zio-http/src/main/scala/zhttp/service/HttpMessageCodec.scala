package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core._
import zhttp.http._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait HttpMessageCodec {
  private val jTrailingHeaders    = new JDefaultHttpHeaders(false)
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest): Request = {
    val url      = URL(Path(jReq.uri()))
    val method   = Method.fromJHttpMethod(jReq.method())
    val headers  = Header.make(jReq.headers())
    val endpoint = method -> url
    val data     = Request.Data(headers, HttpContent.Complete(jReq.content().toString(HTTP_CHARSET)))
    Request(endpoint, data)
  }

  /**
   * Encode the [[Response]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse(jVersion: JHttpVersion, res: Response.HttpResponse): JFullHttpResponse = {
    val jHttpHeaders   =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.set(hh.name, hh.value)
      }
    val jStatus        = res.status.toJHttpStatus
    val jContentBytBuf = res.content match {
      case HttpContent.Complete(data) =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length())
        jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
        jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
        JUnpooled.copiedBuffer(data, HTTP_CHARSET)

      case _ =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
        JUnpooled.buffer(0)
    }

    new JDefaultFullHttpResponse(jVersion, jStatus, jContentBytBuf, jHttpHeaders, jTrailingHeaders)
  }

  /**
   * Converts Request to JFullHttpRequest
   */
  def encodeRequest(jVersion: JHttpVersion, req: Request): JFullHttpRequest = {
    val method  = req.method.asJHttpMethod
    val uri     = req.url.asString
    val content = req.getBodyAsString match {
      case Some(text) => JUnpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => JUnpooled.EMPTY_BUFFER
    }
    val headers = Header.disassemble(req.headers)
    val jReq    = new JDefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }
}
