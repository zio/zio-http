package zhttp.logging
import zhttp.logging.LoggerTransport.Transport
import zio.test._

import scala.collection.mutable.ListBuffer

object LoggerSpec extends DefaultRunnableSpec {

  final class MemoryLogger() extends Transport {
    val buffer: ListBuffer[String]            = ListBuffer.empty[String]
    override def run(msg: CharSequence): Unit = buffer += msg.toString
    def stdout: String                        = buffer.mkString("\n")
  }

  private def inMemoryLogTransport(transport: MemoryLogger): LoggerTransport =
    LoggerTransport(
      format = LogFormat.min,
      level = LogLevel.Info,
      transport = transport,
    )

  override def spec = suite("LoggerSpec")(
    test("Multiple transports could be used.") {
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)

      logger.info("This is a test")

      assertTrue(logger.transports.size == 2)

    },
    test("LogLevel is properly set to all transports.") {
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)

      val probe = logger.transports.head
      assertTrue(probe.level == LogLevel.Info)
    },
    test("Should not create any file if there is no content to be added due to content filtering.") {
      val transport    = new MemoryLogger
      val logTransport = LoggerTransport(
        format = LogFormat.min,
        level = LogLevel.Info,
        filter = _.startsWith("[Test]"),
        transport = transport,
      )
      val logger       = Logger.make
        .withTransport(logTransport)
        .withLevel(LogLevel.Info)
      val probe        = "this is a simple line of log for filtering"
      logger.info(probe)
      assertTrue(transport.stdout.isEmpty)
    },
    test("A log line should be stored when log levels are matching.") {
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)
        .withTags("Test")
      val probe     = "this is a simple log message"
      logger.info(probe)
      val result    = transport.stdout
      assertTrue(result.contains(probe))
    },
    test("A log line should not be stored when log levels are not matching.") {
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)
        .withTags("Test")
      val probe     = "this is a simple log message"
      logger.trace(probe)
      assertTrue(transport.stdout.isEmpty)
    },
    test("A log line should start with a tag when log levels are matching.") {
      val tag       = "Server"
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)
        .withTags(tag)
      val probe     = "this is a simple log message"
      logger.info(probe)
      assertTrue(transport.stdout.contains(tag))
    },
    test("A log line should contain a stack trace in case of log level error.") {
      val tag       = "Server"
      val transport = new MemoryLogger
      val logger    = Logger.make
        .withTransport(inMemoryLogTransport(transport))
        .withLevel(LogLevel.Info)
        .withTags(tag)
      val probe     = "this is a simple log message"
      logger.error(probe, new RuntimeException("exception occurred."))
      assertTrue(transport.stdout.contains("FiberContext.scala"))
    },
  )
}
