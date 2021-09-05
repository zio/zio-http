package zhttp.service

import io.netty.handler.codec.http.{DefaultHttpHeaders, DefaultHttpResponse, HttpHeaderNames, HttpHeaders, HttpVersion}
import zhttp.http.{HttpData, Response}
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.netty.handler.codec.http2.{DefaultHttp2Headers, Http2Headers}

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [[io.netty.handler.codec.http.FullHttpResponse]]
   */
  def encodeResponse[R, E](jVersion: HttpVersion, res: Response.HttpResponse[R, E]): DefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { (jh, hh) =>
        jh.add(hh.name, hh.value)
      }
    jHttpHeaders.set(HttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.toJHttpStatus
    res.content match {
      case HttpData.CompleteData(data) =>
        jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, data.length)

      case HttpData.StreamData(_) => ()

      case HttpData.Empty =>
        jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, 0)
    }
    new DefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [[io.netty.handler.codec.http2.Http2Headers]]
   */
  def encodeResponse[R, E](res: Response.HttpResponse[R, E]): Http2Headers = {
    val headers = new DefaultHttp2Headers().status(res.status.toJHttpStatus.codeAsText())
    headers
      .set(HttpHeaderNames.SERVER, SERVER_NAME)
      .set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val length  = res.content match {
      case HttpData.CompleteData(data) => data.length

      case HttpData.StreamData(_) => -1

      case HttpData.Empty => 0
    }
    if (length >= 0) headers.setInt(HttpHeaderNames.CONTENT_LENGTH, length)
    headers
  }
}
