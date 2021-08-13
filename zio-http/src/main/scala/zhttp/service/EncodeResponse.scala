package zhttp.service

import io.netty.handler.codec.http.{DefaultHttpHeaders, DefaultHttpResponse, HttpHeaderNames, HttpHeaders, HttpVersion}
import zhttp.http.{HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
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
}
