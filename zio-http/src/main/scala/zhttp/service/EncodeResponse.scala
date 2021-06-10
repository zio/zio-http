package zhttp.service

import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpVersion => JHttpVersion,
}
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.{HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E, A](jVersion: JHttpVersion, res: Response.Default[R, E, A]): JDefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.set(hh.name, hh.value)
      }
    jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.toJHttpStatus
    res.content match {
      case HttpData.CompleteContent(data) => jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length)
      case HttpData.BufferedContent(_)    => ()
      case HttpData.EmptyContent          => jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
    }
    new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }
}
