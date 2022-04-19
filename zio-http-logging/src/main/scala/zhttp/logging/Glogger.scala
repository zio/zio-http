package zhttp.logging

/**
 * Core Logger class.
 *
 * TODO: Rename to Logger later
 */
final case class Glogger(transport: List[LoggerTransport]) { self =>
  def foreachTransport(f: LoggerTransport => LoggerTransport): Glogger = Glogger(transport.map(t => f(t)))
  def withFormat(format: LogLine => CharSequence): Glogger             = foreachTransport(_.withFormat(format))
  def withLevel(level: LogLevel): Glogger                              = foreachTransport(_.withLevel(level))
  def withTransport(transport: LoggerTransport): Glogger               = copy(transport = transport :: self.transport)
}

object Glogger {
  def make: Glogger = Glogger(Nil)
}
