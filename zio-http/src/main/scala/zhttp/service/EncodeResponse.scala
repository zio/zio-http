package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpResponse, DefaultHttpResponse => JDefaultHttpResponse, HttpHeaderNames => JHttpHeaderNames, HttpVersion => JHttpVersion}
import zhttp.core.{JDefaultHttpHeaders, JHttpHeaders}
import zhttp.http.{HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"
  private val jTrailingHeaders    = new JDefaultHttpHeaders(false)

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E](jVersion: JHttpVersion, res: Response.HttpResponse[R, E]): JDefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[JHttpHeaders](new JDefaultHttpHeaders()) { (jh, hh) =>
        jh.set(hh.name, hh.value)
      }
    jHttpHeaders.set(JHttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.toJHttpStatus
    res.content match {
      case HttpData.CompleteData(data) => {
        if(!res.headers.exists(h => h.name == JHttpHeaderNames.CONTENT_LENGTH))
          jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.length)
        else ()
        new DefaultFullHttpResponse(jVersion, jStatus, Unpooled.wrappedBuffer(data.toArray), jHttpHeaders, jTrailingHeaders)
      }

      case HttpData.StreamData(_) => new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)

      case HttpData.Empty =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
        new DefaultFullHttpResponse(jVersion, jStatus)
    }
  }
}
