package zhttp.service

import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpVersion => JHttpVersion,
}
import io.netty.handler.codec.http2.{DefaultHttp2Headers => JDefaultHttp2Headers, Http2Headers => JHttp2Headers}
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.{HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E](jVersion: JHttpVersion, res: Response.HttpResponse[R, E]): JDefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.add(hh.name, hh.value)
      }
    jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.toJHttpStatus
    res.content match {
      case HttpData.CompleteData(data) =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length)

      case HttpData.StreamData(_) => ()

      case HttpData.Empty =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
    }
    new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }

  def encodeResponse[R, E](res: Response.HttpResponse[R, E]): JHttp2Headers = {
    val headers = new JDefaultHttp2Headers().status(res.status.toJHttpStatus.codeAsText())
    headers
      .set(JHttpHeaderNames.SERVER, SERVER_NAME)
      .set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val length  = res.content match {
      case HttpData.CompleteData(data) => data.length

      case HttpData.StreamData(_) => -1

      case HttpData.Empty => 0
    }
    if (length >= 0) headers.setInt(JHttpHeaderNames.CONTENT_LENGTH, length)
    headers
  }
}
