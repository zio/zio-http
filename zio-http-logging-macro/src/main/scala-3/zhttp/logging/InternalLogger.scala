package zhttp.logging

final class InternalLogger(val frontend: LogFrontend) {

  import LogLevel._

  import scala.language.experimental.macros

  inline def trace(inline msg: String, tags: List[String]): Unit = ${traceM('frontend)('msg)('tags)}
  inline def debug(inline msg: String, tags: List[String]): Unit = ${debugM('frontend)('msg)('tags)}
  inline def info(inline msg: String, tags: List[String]): Unit = ${infoM('frontend)('msg)('tags)}
  inline def warn(inline msg: String, tags: List[String]): Unit = ${warnM('frontend)('msg)('tags)}

  inline def error(inline msg: String, inline throwable: Throwable, tags: List[String]): Unit =  ${errorTM('frontend)('throwable)('msg)('tags)}
  inline def error(inline msg: String, tags: List[String]): Unit = ${errorM('frontend)('msg)('tags)}

}

object InternalLogger {
  def make(
            name: String,
            level: LogLevel = LogLevel.ERROR,
            format: Setup.LogFormat = LogFormat.default,
            filter: String => Boolean = _ => true,
          ): InternalLogger =
    make(Config(name, level, format, filter))

  def make(config: Config): InternalLogger =
    new InternalLogger(LogFrontend.default(config))
}
