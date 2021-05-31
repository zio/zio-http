package zhttp.service

import io.netty.handler.codec.http.{
  DefaultHttpHeaders => JDefaultHttpHeaders,
  DefaultHttpResponse => JDefaultHttpResponse,
  HttpHeaderNames => JHttpHeaderNames,
  HttpVersion => JHttpVersion,
}
import zhttp.http.{HttpData, Response}

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private[zhttp] trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E](jVersion: JHttpVersion, res: Response.HttpResponse[R, E]): JDefaultHttpResponse = {
    val jHttpHeaders = new JDefaultHttpHeaders()
      .set(JHttpHeaderNames.SERVER, SERVER_NAME)
      .set(JHttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")

    var header = res.headers
    while (header.nonEmpty) {
      jHttpHeaders.set(header.head.name, header.head.value)
      header = header.tail
    }

    val jStatus = res.status.toJHttpStatus

    res.content match {
      case HttpData.CompleteData(data) =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, data.asJava.readableBytes)
      case HttpData.Empty              =>
        jHttpHeaders.set(JHttpHeaderNames.CONTENT_LENGTH, 0)
      case _                           => ()
    }
    new JDefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }
}
