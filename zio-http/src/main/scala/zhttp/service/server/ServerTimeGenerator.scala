package zhttp.service.server

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpResponse}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

case class ServerTimeGenerator() {
  private val formatter = DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

  def update(headers: HttpHeaders): HttpHeaders = {
    headers.set(HttpHeaderNames.DATE, formatter.format(Instant.now()))
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
