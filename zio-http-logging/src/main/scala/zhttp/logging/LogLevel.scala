package zhttp.logging

sealed trait LogLevel { self =>
  def name: String
  def level: Int
  private[zhttp] def methodName    = name.toLowerCase
  def >=(right: LogLevel): Boolean = right.level >= self.level
}

/**
 * Defines standard log levels.
 */
object LogLevel {
  case object OFF   extends LogLevel {
    override def name: String = ""
    override def level: Int   = 999
  }
  case object TRACE extends LogLevel {
    override def name: String = "TRACE"
    override def level: Int   = 1
  }
  case object DEBUG extends LogLevel {
    override def name: String = "DEBUG"
    override def level: Int   = 2
  }
  case object INFO  extends LogLevel {
    override def name: String = "INFO"
    override def level: Int   = 3
  }
  case object WARN  extends LogLevel {
    override def name: String = "WARN"
    override def level: Int   = 4
  }
  case object ERROR extends LogLevel {
    override def name: String = "ERROR"
    override def level: Int   = 5
  }

}
