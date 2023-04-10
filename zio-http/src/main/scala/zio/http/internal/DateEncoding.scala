package zio.http.internal

import java.time.ZonedDateTime

import zio.http.netty.NettyDateEncoding

private[http] trait DateEncoding {
  def encodeDate(date: ZonedDateTime): String
  def decodeDate(date: String): Option[ZonedDateTime]
}

private[http] object DateEncoding {
  val default: DateEncoding = NettyDateEncoding
}
