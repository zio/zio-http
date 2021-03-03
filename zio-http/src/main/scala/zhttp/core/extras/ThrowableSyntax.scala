package zhttp.core.extras

import java.io.{PrintWriter, StringWriter}

trait ThrowableSyntax {
  implicit final class ThrowableSyntax(throwable: Throwable) {

    /**
     * Outputs a reasonable looking stacktrace as string
     */
    def getStackAsString: String = {
      val sw = new StringWriter
      throwable.printStackTrace(new PrintWriter(sw))
      sw.toString
    }
  }
}
