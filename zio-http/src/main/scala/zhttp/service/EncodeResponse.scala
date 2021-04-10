package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core.{JDefaultFullHttpResponse, JDefaultHttpHeaders, JFullHttpResponse, JHttpHeaders}
import zhttp.http.{HTTP_CHARSET, HttpContent, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"
  private val jTrailingHeaders    = new JDefaultHttpHeaders(false)

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R](jVersion: JHttpVersion, res: Response.HttpResponse[R]): JFullHttpResponse = {
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
}
