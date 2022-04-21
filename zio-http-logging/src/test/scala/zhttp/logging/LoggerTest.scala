package zhttp.logging

object LoggerTest {

  def main(args: Array[String]): Unit = {
    val log = Logger.make
      .withTransport(LoggerTransport.console("test"))
      .withLevel(LogLevel.TRACE)
      .withFormat(LogFormat.default)

    log.trace("a trace log", List("netty"))
  }

}
