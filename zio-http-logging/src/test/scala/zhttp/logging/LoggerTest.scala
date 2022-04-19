package zhttp.logging

object LoggerTest {

  def main(args: Array[String]): Unit = {
    val log = Logger.make("test")
    log.error("a trace log", List("netty"))
  }

}
