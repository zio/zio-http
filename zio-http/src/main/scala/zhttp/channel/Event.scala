package zhttp.channel

/**
 * Represents all the incoming events on a channel
 */
sealed trait Event[+A]
object Event {
  case object Register                 extends Event[Nothing]
  case class Read[A](data: A)          extends Event[A]
  case class Failure(cause: Throwable) extends Event[Nothing]
  case object Complete                 extends Event[Nothing]
}
