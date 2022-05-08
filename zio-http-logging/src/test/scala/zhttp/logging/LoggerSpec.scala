package zhttp.logging
import zio.test._

import java.nio.file.{Files, Paths}

object LoggerSpec extends DefaultRunnableSpec {
  private val logFile             = Paths.get("target/file.log")
  private val consoleLogTransport = LoggerTransport.console
  private val fileLogTransport    = LoggerTransport.file(logFile)
  override def spec               = suite("LoggerSpec")(
    test("multiple transports could be used") {
      val logger = Logger.make
        .withTransport(consoleLogTransport)
        .withTransport(LoggerTransport.console)
        .withLevel(LogLevel.Info)

      logger.info("This is a test")

      assertTrue(logger.transports.size == 2)

    },
    test("LogLevel is properly set to all transports.") {
      val logger = Logger.make
        .withTransport(consoleLogTransport)
        .withLevel(LogLevel.Info)

      val probe = logger.transports.head
      assertTrue(probe.level == LogLevel.Info)
    },
    test("Logger is able to log to a file.") {
      val logger      = Logger.make
        .withTransport(fileLogTransport)
        .withLevel(LogLevel.Info)
      val probe       = "this is a simple line of log"
      logger.info(probe)
      val fileContent = Files.readAllLines(logFile).iterator()
      val content     = if (fileContent.hasNext) fileContent.next() else ""
      Files.deleteIfExists(logFile)
      assertTrue(content.contains(probe))
    },
    test("File transport should not create any file if there is no content to be added due to content filtering.") {
      val localLogFile  = Paths.get("target/another.log")
      val logWithFilter = LoggerTransport.file(localLogFile).withFilter(line => line.contains("Test"))
      val logger        = Logger.make
        .withTransport(logWithFilter)
        .withLevel(LogLevel.Info)
      val probe         = "this is a simple line of log for filtering"
      logger.info(probe)
      val isFilePresent = Files.exists(localLogFile)
      assertTrue(!isFilePresent)
    },
    test("File transport should not create any file if there is no content to be added due to log level.") {
      val logger        = Logger.make
        .withTransport(fileLogTransport)
        .withLevel(LogLevel.Info)
      val probe         = "this is a simple line of log"
      logger.trace(probe)
      val isFilePresent = Files.exists(logFile)
      assertTrue(!isFilePresent)
    },
  )
}
