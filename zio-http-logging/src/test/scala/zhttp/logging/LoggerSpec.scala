package zhttp.logging
import zio.test._

import scala.collection.mutable.ListBuffer

object LoggerSpec extends DefaultRunnableSpec {

  override def spec = suite("LoggerSpec")(
    testM("logs nothing") {
      val message = "ABC"
      val logger  = Logger.make.withLevel(LogLevel.Error)

      checkAll(Gen.fromIterable(LogLevel.all.filter(_ != LogLevel.Error))) { level =>
        val transport = MemoryTransport.make
        logger.withTransport(transport).dispatch(message, level)
        assertTrue(transport.stdout == "")
      }
    },
    testM("logs message") {
      val format  = LogFormat.logLevel |-| LogFormat.msg
      val message = "ABC"
      checkAll(Gen.fromIterable(LogLevel.all)) { level =>
        val transport = MemoryTransport.make
        Logger.make.withTransport(transport).withLevel(LogLevel.Trace).withFormat(format).dispatch(message, level)
        assertTrue(transport.stdout == s"${level} ABC")
      }
    },
  )

  final class MemoryTransport extends LoggerTransport() {
    val buffer: ListBuffer[String] = ListBuffer.empty[String]
    def reset(): Unit              = buffer.clear()
    def stdout: String             = buffer.mkString("\n")

    override def run(charSequence: CharSequence): Unit = buffer += charSequence.toString

  }

  object MemoryTransport {
    def make: MemoryTransport = new MemoryTransport
  }
}
