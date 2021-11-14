package zhttp.service.server

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpResponse}

import java.text.SimpleDateFormat
import java.util.Date

case class ServerTimeGenerator() {
  private var last: Long = 0
  private val formatter  = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def update(headers: HttpHeaders): Boolean = {
    val now = System.currentTimeMillis()
    if (now - last >= 1000) {
      last = now
      headers.set(HttpHeaderNames.DATE, formatter.format(new Date(now)))
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
