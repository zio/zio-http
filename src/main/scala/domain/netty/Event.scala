package zio-http.domain.netty

sealed trait Event[+O]

object Event {

  /*
   * Fired when an exception is thrown
   */
  final case class ExceptionCaught(cause: Throwable) extends Event[Nothing]

  /*
   * Fired when a message is read
   */
  final case class Read[R, E, O](message: O) extends Event[O]

  /*
   * Fired when a user event is triggered
   */
  final case class UserEventTriggered(message: Any) extends Event[Nothing]

  /*
   * Fired when channel becomes active
   */
  final case object Active extends Event[Nothing]

  /*
   * Fired when the channel becomes inactive
   */
  final case object Inactive extends Event[Nothing]

  /*
   * Fired when all the messages have been read
   */
  final case object ReadComplete extends Event[Nothing]

  /*
   * Fired when the channel is registered
   */
  final case object Registered extends Event[Nothing]

  /*
   * Fired when the channel is unregistered
   */
  final case object Unregistered extends Event[Nothing]

  /*
   * Fired when the channel's writability changes
   */
  final case object WritabilityChanged extends Event[Nothing]
}
