package zhttp.service

import io.netty.handler.codec.http.{
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpVersion => JHttpVersion,
}
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.{HasContent, HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E, A: HasContent](jVersion: JHttpVersion, res: Response[R, E, A]): JDefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.set(hh.name, hh.value)
      }
    jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.toJHttpStatus
    res match {
      case Response.Default(_, _, dContent) =>
        dContent match {
          case HttpData.CompleteContent(data) => jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length)
          case HttpData.BufferedContent(_)    => ()
          case HttpData.EmptyContent          => jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
        }
      case _                                => ()
    }
    new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }
}
