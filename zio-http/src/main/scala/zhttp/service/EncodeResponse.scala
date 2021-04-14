package zhttp.service

import io.netty.buffer.{Unpooled => JUnpooled}
import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpVersion => JHttpVersion,
}
import zhttp.core.{JDefaultFullHttpResponse, JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.{HttpContent, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"
  private val jTrailingHeaders    = new JDefaultHttpHeaders(false)

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R](jVersion: JHttpVersion, res: Response.HttpResponse[R]): JDefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.set(hh.name, hh.value)
      }
    val jStatus      = res.status.toJHttpStatus
    val response     = res.content match {
      case HttpContent.Complete(data) =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length)
        jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
        jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
        new JDefaultFullHttpResponse(
          jVersion,
          jStatus,
          JUnpooled.copiedBuffer(data.toArray),
          jHttpHeaders,
          jTrailingHeaders,
        )
      case HttpContent.Chunked(_)     =>
        jHttpHeaders.set(JHttpHeaderNames.TRANSFER_ENCODING, "Chunked")
        jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
        jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
        new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
      case _                          =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)

        new JDefaultFullHttpResponse(jVersion, jStatus, JUnpooled.buffer(0), jHttpHeaders, jTrailingHeaders)
    }

    response
  }
}
