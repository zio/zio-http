package zhttp.logging
import java.nio.file.Paths

object LoggerTest {

  def main(args: Array[String]): Unit = {
    val log = Logger.make
      .withTransport(LoggerTransport.console("test"))
      .withTransport(LoggerTransport.file("FileLogger", Paths.get("target/file_logger.log")))
      .withLevel(LogLevel.TRACE)

    log.trace("a trace log", List("netty"))
  }

}
