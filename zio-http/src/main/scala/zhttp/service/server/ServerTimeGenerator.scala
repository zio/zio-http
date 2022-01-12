package zhttp.service.server

import java.text.SimpleDateFormat
import java.util.Date

private[zhttp] final class ServerTimeGenerator(minDuration: Long) {
  private val formatter          = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")
  private var last: Long         = System.currentTimeMillis()
  private var lastString: String = formatter.format(new Date(last))

  def get: String = lastString

  def getLong: Long = last

  def refreshAndGet(): String = {
    canUpdate()
    get
  }

  def canUpdate(): Boolean = {
    val now = System.currentTimeMillis()
    if (now - last >= minDuration) {
      last = now
      lastString = formatter.format(new Date(last))
      true
    } else {
      false
    }
  }
}

object ServerTimeGenerator {
  def make: ServerTimeGenerator = new ServerTimeGenerator(1000) // Update time every 1 second
}
