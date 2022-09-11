package zio.logging

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

  final def toTransport: LoggerTransport = LoggerTransport.empty.withLevel(self)
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
    sys.env.get(name).flatMap(fromString)

  /**
   * Automatically detects the level from the system properties
   */
  def detectFromProps(name: String): Option[LogLevel] =
    sys.props.get(name).flatMap(fromString)

  /**
   * Detects the LogLevel given any random string
   */
  def fromString(string: String): Option[LogLevel] = string.toUpperCase match {
    case "TRACE" => Option(Trace)
    case "DEBUG" => Option(Debug)
    case "INFO"  => Option(Info)
    case "WARN"  => Option(Warn)
    case "ERROR" => Option(Error)
    case _       => None
  }

  case object Trace extends LogLevel(1)

  case object Debug extends LogLevel(2)

  case object Info extends LogLevel(3)

  case object Warn extends LogLevel(4)

  case object Error extends LogLevel(5)
}
