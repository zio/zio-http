package zhttp.service.server

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpResponse}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

case class ServerTimeGenerator() {
  private var last: Instant = Instant.now()
  private val formatter     = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

  def update(headers: HttpHeaders): Boolean = {
    val now = Instant.now()
    if (now.toEpochMilli - last.toEpochMilli >= 1000) {
      last = now
      headers.set(HttpHeaderNames.DATE, formatter.format(now))
      true
    } else false
  }

  def update(response: HttpResponse): HttpResponse = {
    update(response.headers())
    response
  }
}

object ServerTimeGenerator {
  def make: ServerTimeGenerator = {
    new ServerTimeGenerator()
  }
}
