package zhttp.service.server

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpResponse}

import java.text.SimpleDateFormat
import java.util.Date

case class ServerTimeGenerator() {
  private val formatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def update(headers: HttpHeaders): Boolean = {
    headers.set(HttpHeaderNames.DATE, formatter.format(new Date()))
    true
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
