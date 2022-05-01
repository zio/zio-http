package zhttp.logging

sealed trait LogLevel { self =>
  private[zhttp] def methodName    = name.toLowerCase
  def >=(right: LogLevel): Boolean = right.level >= self.level
  def level: Int
  def name: String
}

/**
 * Defines standard log levels.
 */
object LogLevel {

  /**
   * Automatically detects the level from the environment variable - "ZHTTP_LOG"
   */
  def detectFromEnv(name: String): LogLevel = {
    sys.env.getOrElse(name, Debug.name).toUpperCase match {
      case "TRACE" => Trace
      case "DEBUG" => Debug
      case "INFO"  => Info
      case "WARN"  => Warn
      case "ERROR" => Error
      case _       => Disable
    }
  }

  case object Disable extends LogLevel {
    override def level: Int   = 999
    override def name: String = ""
  }

  case object Trace extends LogLevel {
    override def level: Int   = 1
    override def name: String = "TRACE"
  }

  case object Debug extends LogLevel {
    override def level: Int   = 2
    override def name: String = "DEBUG"
  }

  case object Info extends LogLevel {
    override def level: Int   = 3
    override def name: String = "INFO"
  }

  case object Warn extends LogLevel {
    override def level: Int   = 4
    override def name: String = "WARN"
  }

  case object Error extends LogLevel {
    override def level: Int   = 5
    override def name: String = "ERROR"
  }
}
