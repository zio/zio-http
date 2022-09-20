package zio.http.service

import io.netty.util.AsciiString

import java.text.SimpleDateFormat
import java.util.Date
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

private[zio] final class ServerTime(minDuration: Long) {

  import ServerTime.log

  private var last: Long               = System.currentTimeMillis()
  private var lastString: CharSequence = ServerTime.format(new Date(last))

  def refresh(): Boolean = {
    val now  = System.currentTimeMillis()
    val diff = now - last
    if (diff > minDuration) {
      log.debug(s"Server time threshold (${minDuration}) exceeded: [${diff}]")
      last = now
      lastString = ServerTime.format(new Date(last))
      true
    } else {
      false
    }
  }

  def get: CharSequence = lastString

  def refreshAndGet(): CharSequence = {
    refresh()
    get
  }
}

object ServerTime {
  val log            = Log.withTags("Time")
  private val format = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def format(d: Date): CharSequence = new AsciiString(format.format(d))

  def make(interval: zio.Duration): ServerTime = new ServerTime(interval.toMillis)

  def parse(s: CharSequence): Date = format.parse(s.toString)
}
