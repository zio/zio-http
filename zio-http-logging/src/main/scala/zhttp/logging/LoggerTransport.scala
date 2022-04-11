package zhttp.logging

import zhttp.logging.LogFrontend.LogLine
import zhttp.logging.LoggerTransport.Transport

/**
 * Provides a way to build and configure transports for logging. Transports are
 * used to, format and serialize LogLines and them to a backend.
 */
final case class LoggerTransport(level: LogLevel, format: _ => CharSequence, transport: Transport) { self =>
  def withFormat(format: LogLine => CharSequence): LoggerTransport = self.copy(format = format)
  def withFormat(format: LogFormat): LoggerTransport               = self.copy(format = format(_))
  def withLevel(level: LogLevel): LoggerTransport                  = self.copy(level = level)
}

object LoggerTransport {
  def console: LoggerTransport = LoggerTransport(
    level = LogLevel.OFF,
    format = LogFormat.default(_),
    transport = Transport.UnsafeSync(println),
  )

  sealed trait Transport { self =>
    def run(line: CharSequence): Unit =
      self match {
        case Transport.UnsafeSync(log) => log(line)
        case Transport.Empty           => ()
      }
  }

  object Transport {
    final case class UnsafeSync(log: CharSequence => Unit) extends Transport
    case object Empty                                      extends Transport
  }
}
