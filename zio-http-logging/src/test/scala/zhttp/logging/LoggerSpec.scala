package zhttp.logging
import zhttp.logging.LoggerTransport.Transport
import zhttp.logging.LoggerTransport.Transport.unsafeSync
import zio.test._

import java.nio.file.{Files, Paths}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object LoggerSpec extends DefaultRunnableSpec {

  private def imMemoryTransport(ref: mutable.ListBuffer[CharSequence]): Transport = unsafeSync { line =>
    ref.addOne(line)
  }

  private def inMemoryLogTransport(ref: mutable.ListBuffer[CharSequence]): LoggerTransport =
    LoggerTransport(
      format = LogFormat.min,
      level = LogLevel.Info,
      transport = imMemoryTransport(ref),
    )

  private val logFile             = Paths.get("target/file.log")
  private val consoleLogTransport = LoggerTransport.console
  private val fileLogTransport    = LoggerTransport.file(logFile)
  override def spec               = suite("LoggerSpec")(
    test("Multiple transports could be used") {
      val logger = Logger.make
        .withTransport(consoleLogTransport)
        .withTransport(inMemoryLogTransport(ListBuffer.empty[CharSequence]))
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
    test("Should not create any file if there is no content to be added due to content filtering.") {
      val ref          = ListBuffer.empty[CharSequence]
      val logTransport = LoggerTransport(
        format = LogFormat.min,
        level = LogLevel.Info,
        filter = _.startsWith("[Test]"),
        transport = imMemoryTransport(ref),
      )
      val logger       = Logger.make
        .withTransport(logTransport)
        .withLevel(LogLevel.Info)
      val probe        = "this is a simple line of log for filtering"
      logger.info(probe)
      assertTrue(ref.isEmpty)
    },
    test("A log line should be stored when log levels are matching.") {
      val ref    = ListBuffer.empty[CharSequence]
      val logger = Logger.make
        .withTransport(inMemoryLogTransport(ref))
        .withLevel(LogLevel.Info)
        .withTags("Test")
      val probe  = "this is a simple log message"
      logger.info(probe)
      val result = ref.headOption.getOrElse("")
      assertTrue(result.toString.contains(probe))
    },
    test("A log line should not be stored when log levels are not matching.") {
      val ref    = ListBuffer.empty[CharSequence]
      val logger = Logger.make
        .withTransport(inMemoryLogTransport(ref))
        .withLevel(LogLevel.Info)
        .withTags("Test")
      val probe  = "this is a simple log message"
      logger.trace(probe)
      assertTrue(ref.isEmpty)
    },
    test("A log line should start with a tag when log levels are matching.") {
      val tag    = "Server"
      val ref    = ListBuffer.empty[CharSequence]
      val logger = Logger.make
        .withTransport(inMemoryLogTransport(ref))
        .withLevel(LogLevel.Info)
        .withTags(tag)
      val probe  = "this is a simple log message"
      logger.info(probe)
      val result = ref.headOption.getOrElse("")
      assertTrue(result.toString.contains(tag))
    },
    test("A log line should contain a stack trace in case of log level error.") {
      val tag    = "Server"
      val ref    = ListBuffer.empty[CharSequence]
      val logger = Logger.make
        .withTransport(inMemoryLogTransport(ref))
        .withLevel(LogLevel.Info)
        .withTags(tag)
      val probe  = "this is a simple log message"
      logger.error(probe, new RuntimeException("exception occurred."))
      assertTrue(ref.mkString.contains("FiberContext.scala"))
    },
  )
}
