package zhttp.service

import io.netty.channel.ChannelHandlerContext

/**
 * Immutable and type-safe representation of events that are triggered on a
 * netty channel. `A` represents the inbound message type and `B` represents the
 * outbound message type that's allowed on the channel.
 */
final case class ChannelEvent[-A, +B](channel: Channel[A], event: ChannelEvent.Event[B]) { self =>
  def contramap[A1](f: A1 => A): ChannelEvent[A1, B] = copy(channel = channel.contramap(f))
  def map[B1](f: B => B1): ChannelEvent[A, B1]       = copy(event = event.map(f))
}

object ChannelEvent {
  def channelRead[B](ctx: ChannelHandlerContext, msg: B): ChannelEvent[Any, B] =
    ChannelEvent(Channel.make(ctx.channel()), Event.ChannelRead(msg))

  def channelRegistered(ctx: ChannelHandlerContext): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), Event.ChannelRegistered)

  def channelUnregistered(ctx: ChannelHandlerContext): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), Event.ChannelUnregistered)

  def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), Event.ExceptionCaught(cause))

  def userEventTriggered(ctx: ChannelHandlerContext, evt: UserEvent): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), Event.UserEventTriggered(evt))

  sealed trait Event[+A] { self =>
    def map[B](f: A => B): Event[B] = self match {
      case Event.ChannelRead(msg)              => Event.ChannelRead(f(msg))
      case Event.ChannelRegistered             => Event.ChannelRegistered
      case Event.ChannelUnregistered           => Event.ChannelUnregistered
      case event @ Event.ExceptionCaught(_)    => event
      case event @ Event.UserEventTriggered(_) => event
    }
  }

  object Event {
    final case class ExceptionCaught(cause: Throwable) extends Event[Nothing]
    final case class ChannelRead[A](message: A)        extends Event[A]
    case class UserEventTriggered(event: UserEvent)    extends Event[Nothing]
    case object ChannelRegistered                      extends Event[Nothing]
    case object ChannelUnregistered                    extends Event[Nothing]
  }

  sealed trait UserEvent
  object UserEvent {
    case object HandshakeTimeout  extends UserEvent
    case object HandshakeComplete extends UserEvent
  }
}
