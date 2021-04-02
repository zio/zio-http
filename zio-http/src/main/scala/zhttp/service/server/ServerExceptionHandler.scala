package zhttp.service.server

import java.io.IOException

/**
 * Default Http exception handler that return a boolean
 */
trait ServerExceptionHandler {
  def canThrowException(cause: Throwable): Boolean = {
    cause match {
      case m: IOException =>
        m.getMessage match {
          case null => true
          case msg  => !msg.toLowerCase.matches(".*connection reset by peer.*")
        }
      case _              => true
    }
  }
}
