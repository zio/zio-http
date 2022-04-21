package zhttp.logging

/**
 * Core Logger class.
 */
final case class Logger(transports: List[LoggerTransport]) { self =>
  def foreachTransport(f: LoggerTransport => LoggerTransport): Logger = Logger(transports.map(t => f(t)))
  def withFormat(format: Setup.LogFormat): Logger                     = foreachTransport(_.withFormat(format))
  def withLevel(level: LogLevel): Logger                              = foreachTransport(_.withLevel(level))
  def withTransport(transport: LoggerTransport): Logger               = copy(transports = transport :: self.transports)
  def withFilter(filter: String => Boolean): Logger                   = foreachTransport(_.withFilter(filter))

  def trace(msg: String, tags: List[String]): Unit                       = log(LogLevel.TRACE, msg, None, tags)
  def debug(msg: String, tags: List[String]): Unit                       = log(LogLevel.DEBUG, msg, None, tags)
  def info(msg: String, tags: List[String]): Unit                        = log(LogLevel.INFO, msg, None, tags)
  def warn(msg: String, tags: List[String]): Unit                        = log(LogLevel.WARN, msg, None, tags)
  def error(msg: String, tags: List[String]): Unit                       = log(LogLevel.ERROR, msg, None, tags)
  def error(msg: String, throwable: Throwable, tags: List[String]): Unit =
    log(LogLevel.ERROR, msg, Some(throwable), tags)

  private def log(level: LogLevel, msg: String, throwable: Option[Throwable], tags: List[String]) = {
    transports.filter(_.level == level).foreach { transport =>
      val logger = InternalLogger.make(transport)
      level match {
        case LogLevel.OFF   => ()
        case LogLevel.TRACE => logger.trace(msg, tags)
        case LogLevel.DEBUG => logger.debug(msg, tags)
        case LogLevel.INFO  => logger.info(msg, tags)
        case LogLevel.WARN  => logger.warn(msg, tags)
        case LogLevel.ERROR => throwable.fold(logger.error(msg, tags))(err => logger.error(msg, err, tags))
      }

    }
  }
}

object Logger {
  def make: Logger                            = Logger(Nil)
  def makeConsoleLogger(name: String): Logger = Logger(Nil)
    .withTransport(LoggerTransport.console(name))
}
