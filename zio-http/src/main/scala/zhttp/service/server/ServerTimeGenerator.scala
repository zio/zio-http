package zhttp.service.server

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaders, HttpResponse}
import io.netty.util.concurrent.FastThreadLocal

import java.text.SimpleDateFormat
import java.util.Date

case class ServerTimeGenerator() {
  private val iTime = System.currentTimeMillis()

  private val last: FastThreadLocal[Long] = new FastThreadLocal[Long] {
    override def initialValue(): Long = iTime
  }

  private val formatter = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def update(headers: HttpHeaders): Boolean = {

    val now = System.currentTimeMillis()

    if (now - last.get() >= 1000) {
      last.set(now)
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
