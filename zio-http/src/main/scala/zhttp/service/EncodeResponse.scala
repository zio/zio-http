package zhttp.service

import io.netty.buffer.ByteBufUtil
import io.netty.handler.codec.http.{DefaultHttpHeaders, DefaultHttpResponse, HttpHeaderNames, HttpHeaders, HttpVersion}
import zhttp.experiment.Content
import zhttp.http.{HttpData, Response}
import zio.Chunk

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

trait EncodeResponse {
  private val SERVER_NAME: String = "ZIO-Http"

  /**
   * Encode the [[zhttp.http.UHttpResponse]] to [io.netty.handler.codec.http.FullHttpResponse]
   */
  def encodeResponse[R, E](jVersion: HttpVersion, res: Response[R, E]): DefaultHttpResponse = {
    val jHttpHeaders =
      res.headers.foldLeft[HttpHeaders](new DefaultHttpHeaders()) { (jh, hh) =>
        jh.add(hh.name, hh.value)
      }
    jHttpHeaders.set(HttpHeaderNames.SERVER, SERVER_NAME)
    jHttpHeaders.set(HttpHeaderNames.DATE, s"${DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now)}")
    val jStatus      = res.status.asJava
    res.data match {
      case HttpData.HttpContent(data) =>
        data match {
          case Content.Text(text, _) => jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, text.length)
          case Content.Binary(data)  => jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, data.length)
          case Content.BinaryN(data) =>
            jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, Chunk.fromArray(ByteBufUtil.getBytes(data)).length)
          case _                     => jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, 0)
        }

      case HttpData.Empty =>
        jHttpHeaders.set(HttpHeaderNames.CONTENT_LENGTH, 0)

      case _ => ()
    }
    new DefaultHttpResponse(jVersion, jStatus, jHttpHeaders)
  }
}
