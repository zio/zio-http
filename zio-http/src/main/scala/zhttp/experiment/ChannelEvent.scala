package zhttp.experiment

sealed trait ChannelEvent[+A]

object ChannelEvent {
  case object Registered      extends ChannelEvent[Nothing]
  case object Unregistered    extends ChannelEvent[Nothing]
  case object Complete        extends ChannelEvent[Nothing]
  case class Read[A](data: A) extends ChannelEvent[A]
}
