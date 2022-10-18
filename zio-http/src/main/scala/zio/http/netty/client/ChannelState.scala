package zio.http.netty.client

sealed trait ChannelState { self =>
  def &&(other: ChannelState): ChannelState =
    (self, other) match {
      case (ChannelState.Reusable, ChannelState.Reusable) => ChannelState.Reusable
      case _                                              => ChannelState.Invalid
    }
}

object ChannelState {
  case object Invalid  extends ChannelState
  case object Reusable extends ChannelState
}
