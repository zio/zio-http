package zio.http.api.internal

import scala.util.control.{ControlThrowable, NoStackTrace}

case object HaltException extends ControlThrowable("This exception is used only for control flow purposes")
