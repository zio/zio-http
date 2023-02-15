package zio.http.api.internal

import scala.util.control.NoStackTrace

case object HaltException
    extends RuntimeException("This exception is used only for control flow purposes")
    with NoStackTrace
