package zio-http.domain.netty

sealed trait Operation[+A] extends Product with Serializable { self =>

  def ++[A1 >: A](other: Operation[A1]): Operation[A1] =
    Operation.Concat(self, other)

  def <>[A1 >: A](other: Operation[A1]): Operation[A1] =
    Operation.OrElse(self, other)
}
object Operation {
  case class Concat[A](self: Operation[A], other: Operation[A]) extends Operation[A]
  case class OrElse[A](self: Operation[A], other: Operation[A]) extends Operation[A]

  case class FireChannelRead[A](message: A)        extends Operation[A]
  case class FireExceptionCaught(cause: Throwable) extends Operation[Nothing]
  case class FireUserEventTriggered(data: Any)     extends Operation[Nothing]
  case class WriteAndFlush[A](message: A)          extends Operation[A]
  case class Write[A](message: A)                  extends Operation[A]

  case object Empty                         extends Operation[Nothing]
  case object Close                         extends Operation[Nothing]
  case object FireChannelActive             extends Operation[Nothing]
  case object FireChannelInactive           extends Operation[Nothing]
  case object FireChannelReadComplete       extends Operation[Nothing]
  case object FireChannelRegistered         extends Operation[Nothing]
  case object FireChannelUnregistered       extends Operation[Nothing]
  case object FireChannelWritabilityChanged extends Operation[Nothing]
  case object Flush                         extends Operation[Nothing]

  def write[A](message: A): Operation[A] =
    Operation.Write(message)

  def writeAndFlush[A](msg: A): Operation[A] =
    Operation.WriteAndFlush(msg)

  def close: Operation[Nothing] =
    Operation.Close

  def fireChannelRead[A](msg: A): Operation[A] =
    Operation.FireChannelRead(msg)

  def fireExceptionCaught[C](cause: Throwable): Operation[Nothing] =
    Operation.FireExceptionCaught(cause)

  def fireUserEventTriggered[C](msg: Any): Operation[Nothing] =
    Operation.FireUserEventTriggered(msg)

  def flush: Operation[Nothing] =
    Operation.Flush

  def fireChannelRegistered: Operation[Nothing] =
    Operation.FireChannelRegistered

  def fireChannelUnregistered: Operation[Nothing] =
    Operation.FireChannelUnregistered

  def fireChannelActive: Operation[Nothing] =
    Operation.FireChannelActive

  def fireChannelInactive: Operation[Nothing] =
    Operation.FireChannelInactive

  def fireChannelReadComplete: Operation[Nothing] =
    Operation.FireChannelReadComplete

  def fireChannelWritabilityChanged: Operation[Nothing] =
    Operation.FireChannelWritabilityChanged

  def nothing: Operation[Nothing] =
    Operation.Empty

}
