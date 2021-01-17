package zio.web.websockets.internal

import scala.util.control.NoStackTrace

trait FrameError extends NoStackTrace

object FrameError {
  final case class TooBigData(message: String)     extends FrameError
  final case class WrongCode(message: String)      extends FrameError
  final case class MalformedFrame(message: String) extends FrameError
}
