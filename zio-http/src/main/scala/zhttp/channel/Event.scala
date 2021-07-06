package zhttp.channel

/**
 * Represents all the incoming events on a channel
 */
sealed trait Event[+A] { self =>
  import Event._
  def map[B](ab: A => B): Event[B] =
    self match {
      case Read(data)    => Read(ab(data))
      case msg: TypeLess => msg
    }
}

object Event {
  case class Read[A](data: A) extends Event[A]

  sealed trait TypeLess                extends Event[Nothing]
  case object Register                 extends TypeLess
  case class Failure(cause: Throwable) extends TypeLess
  case object Complete                 extends TypeLess

}
