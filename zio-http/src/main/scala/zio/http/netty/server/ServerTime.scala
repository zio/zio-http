package zio.http.netty.server

import io.netty.util.AsciiString

import java.text.SimpleDateFormat
import java.util.Date // scalafix:ok;

private[zio] final class ServerTime(minDuration: Long) {

  private var last: Long               = System.currentTimeMillis()
  private var lastString: CharSequence = ServerTime.format(new Date(last))

  def refresh(): Boolean = {
    val now  = System.currentTimeMillis()
    val diff = now - last
    if (diff > minDuration) {
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
  private val format = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def format(d: Date): CharSequence = new AsciiString(format.format(d))

  def make(interval: zio.Duration): ServerTime = new ServerTime(interval.toMillis)

  def parse(s: CharSequence): Date = format.parse(s.toString)
}
