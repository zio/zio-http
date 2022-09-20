package zio.http

import io.netty.channel.ChannelHandlerContext
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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
    ChannelEvent(Channel.make(ctx.channel()), ChannelRead(msg))

  def channelRegistered(ctx: ChannelHandlerContext): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), ChannelRegistered)

  def channelUnregistered(ctx: ChannelHandlerContext): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), ChannelUnregistered)

  def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), ExceptionCaught(cause))

  def userEventTriggered(ctx: ChannelHandlerContext, evt: UserEvent): ChannelEvent[Any, Nothing] =
    ChannelEvent(Channel.make(ctx.channel()), UserEventTriggered(evt))

  /**
   * Immutable and type-safe representation of events that are triggered on a
   * channel.
   */
  sealed trait Event[+A] { self =>
    def map[B](f: A => B): Event[B] = self match {
      case ChannelRead(msg)              => ChannelRead(f(msg))
      case ChannelRegistered             => ChannelRegistered
      case ChannelUnregistered           => ChannelUnregistered
      case event @ ExceptionCaught(_)    => event
      case event @ UserEventTriggered(_) => event
    }
  }

  final case class ExceptionCaught(cause: Throwable) extends Event[Nothing]
  final case class ChannelRead[A](message: A)        extends Event[A]
  case class UserEventTriggered(event: UserEvent)    extends Event[Nothing]
  case object ChannelRegistered                      extends Event[Nothing]
  case object ChannelUnregistered                    extends Event[Nothing]

  /**
   * Custom user-events that are triggered within ZIO Http
   */
  sealed trait UserEvent
  object UserEvent {
    case object HandshakeTimeout  extends UserEvent
    case object HandshakeComplete extends UserEvent
  }
}
