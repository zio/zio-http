package zhttp.logging

object LoggerTest {

  def main(args: Array[String]): Unit = {
    val log = Logger.make("test", level = LogLevel.TRACE)
    log.trace("a trace log", List("netty"))
  }

}
