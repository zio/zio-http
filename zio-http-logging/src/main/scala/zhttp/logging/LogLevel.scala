package zhttp.logging

sealed abstract class LogLevel(val id: Int) extends Product with Serializable { self =>
  final def >(other: LogLevel): Boolean = self.id > other.id

  final def >=(other: LogLevel): Boolean = self.id >= other.id

  final def <(other: LogLevel): Boolean = self.id < other.id

  final def <=(other: LogLevel): Boolean = self.id <= other.id

  final def name: String = self match {
    case LogLevel.Trace => "Trace"
    case LogLevel.Debug => "Debug"
    case LogLevel.Info  => "Info"
    case LogLevel.Warn  => "Warn"
    case LogLevel.Error => "Error"
  }

  final override def toString: String = name
}

/**
 * Defines standard log levels.
 */
object LogLevel {

  /**
   * Lists all the possible log levels
   */
  val all: List[LogLevel] = List(
    Debug,
    Error,
    Info,
    Trace,
    Warn,
  )

  /**
   * Automatically detects the level from the environment variable
   */
  def detectFromEnv(name: String): Option[LogLevel] =
    sys.env.get(name).map(fromString)

  /**
   * Automatically detects the level from the system properties
   */
  def detectFromProps(name: String): Option[LogLevel] =
    sys.props.get(name).map(fromString)

  /**
   * Detects the LogLevel given any random string
   */
  def fromString(string: String): LogLevel = string.toUpperCase match {
    case "TRACE" => Trace
    case "DEBUG" => Debug
    case "INFO"  => Info
    case "WARN"  => Warn
    case "ERROR" => Error
    case _       => Error
  }

  case object Trace extends LogLevel(1)

  case object Debug extends LogLevel(2)

  case object Info extends LogLevel(3)

  case object Warn extends LogLevel(4)

  case object Error extends LogLevel(5)
}
