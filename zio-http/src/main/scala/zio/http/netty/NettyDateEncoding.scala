package zio.http.netty

import java.time.{ZoneOffset, ZonedDateTime}
import java.util.Date

import zio.http.internal.DateEncoding

import io.netty.handler.codec.DateFormatter

private[http] object NettyDateEncoding extends DateEncoding {
  override def encodeDate(date: ZonedDateTime): String =
    DateFormatter.format(Date.from(date.toInstant))

  override def decodeDate(date: String): Option[ZonedDateTime] =
    Option(DateFormatter.parseHttpDate(date)).map(date => ZonedDateTime.ofInstant(date.toInstant, ZoneOffset.UTC))
}
