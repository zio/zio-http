package zio.http.netty

import zio.http.internal.DateEncoding

import java.time.{Clock, LocalDateTime, ZoneOffset}
import java.util.concurrent.locks.LockSupport

private object CachedDateHeader {
  lazy val default: CachedDateHeader = new CachedDateHeader()
}

final private class CachedDateHeader(
  clock: Clock = Clock.tickSeconds(ZoneOffset.UTC),
  dateEncoding: DateEncoding = DateEncoding.default,
) {
  private var _headerValue = renderDateHeaderValue(clock.millis())

  {
    val t = new Ticker
    t.setDaemon(true)
    t.setName(s"zio.http.netty.DateHeaderEncoder.Scheduler")
    t.setPriority(Thread.MAX_PRIORITY)
    t.start()
  }

  def get(): String = _headerValue

  private final class Ticker extends Thread {
    override def run(): Unit = {
      val clock0        = clock
      var currentMillis = clock0.millis()
      while (!isInterrupted) {
        LockSupport.parkUntil(currentMillis + 1000)
        currentMillis = clock0.millis()
        updateHeaderValue(currentMillis)
      }
    }
  }

  private def renderDateHeaderValue(epochMilli: Long): String = {
    val dt = LocalDateTime
      .ofEpochSecond(epochMilli / 1000L, 0, ZoneOffset.UTC)
      .atZone(ZoneOffset.UTC)

    dateEncoding.encodeDate(dt)
  }

  private def updateHeaderValue(epochMilli: Long): Unit =
    _headerValue = renderDateHeaderValue(epochMilli)

}
