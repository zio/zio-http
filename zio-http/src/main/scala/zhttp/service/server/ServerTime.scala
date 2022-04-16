package zhttp.service.server

import io.netty.util.AsciiString

import java.text.SimpleDateFormat
import java.util.Date

private[zhttp] final class ServerTime(minDuration: Long) {

  private var last: Long               = System.currentTimeMillis()
  private var lastString: CharSequence = ServerTime.format(new Date(last))

  def canUpdate(): Boolean = {
    val now = System.currentTimeMillis()
    if (now - last >= minDuration) {
      last = now
      lastString = ServerTime.format(new Date(last))
      true
    } else {
      false
    }
  }

  def get: CharSequence = lastString

  def refreshAndGet(): CharSequence = {
    canUpdate()
    get
  }
}

object ServerTime {
  private val format = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def format(d: Date): CharSequence = new AsciiString(format.format(d))

  def make: ServerTime = new ServerTime(1000) // Update time every 1 second

  def parse(s: CharSequence): Date = format.parse(s.toString)
}
