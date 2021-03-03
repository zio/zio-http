package zhttp.service.netty.server

import java.io.IOException

/**
 * Default Http exception handler that return a boolean
 */
trait ServerHttpExceptionHandler {
  def canThrowException(cause: Throwable): Boolean = {
    cause match {
      case m: IOException =>
        m.getMessage match {
          case "Connection reset by peer" => false
          case _                          => true
        }
      case _              => true
    }
  }
}
