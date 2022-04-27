package zhttp.logging
import zio.test._

import java.nio.file.{Files, Paths}
import scala.jdk.CollectionConverters.CollectionHasAsScala

object LoggerSpec extends DefaultRunnableSpec {
  private val logFile             = Paths.get("target/file.log")
  private val consoleLogTransport = LoggerTransport.console("test")
  private val fileLogTransport    = LoggerTransport.file("test-file", logFile)
  override def spec               = suite("LoggerSpec")(
    test("multiple transports could be used") {
      val logger = Logger.make
        .withTransport(consoleLogTransport)
        .withTransport(LoggerTransport.console("test2"))
        .withLevel(LogLevel.INFO)

      logger.info("This is a test", List("tests"))

      assertTrue(logger.transports.size == 2)

    },
    test("LogLevel is properly set to all transports.") {
      val logger = Logger.make
        .withTransport(consoleLogTransport)
        .withLevel(LogLevel.INFO)

      val probe = logger.transports.head
      assertTrue(probe.level == LogLevel.INFO)
    },
    test("Logger is able to log to a file.") {
      val logger  = Logger.make
        .withTransport(fileLogTransport)
        .withLevel(LogLevel.INFO)
      val probe   = "this is a simple line of log"
      logger.info(probe, List.empty)
      val content = Files.readAllLines(logFile).asScala.mkString("\n")
      Files.deleteIfExists(logFile)
      assertTrue(content.contains(probe))
    },
    test("File transport should not create any file if there is no content to be added due to content filtering.") {
      val logWithFilter = fileLogTransport.withFilter(line => line.contains("Test"))
      val logger        = Logger.make
        .withTransport(logWithFilter)
        .withLevel(LogLevel.INFO)
      val probe         = "this is a simple line of log"
      logger.info(probe, List.empty)
      val isFilePresent = Files.exists(logFile)
      assertTrue(!isFilePresent)
    },
    test("File transport should not create any file if there is no content to be added due to log level.") {
      val logger        = Logger.make
        .withTransport(fileLogTransport)
        .withLevel(LogLevel.INFO)
      val probe         = "this is a simple line of log"
      logger.trace(probe, List.empty)
      val isFilePresent = Files.exists(logFile)
      assertTrue(!isFilePresent)
    },
  )
}
