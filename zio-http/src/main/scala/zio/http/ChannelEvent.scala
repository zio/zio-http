/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

/**
 * Immutable and type-safe representation of events that are triggered on a
 * netty channel. `A` represents the inbound message type and `B` represents the
 * outbound message type that's allowed on the channel.
 */
sealed trait ChannelEvent[+A] { self =>
  import ChannelEvent._

  def map[B](f: A => B): ChannelEvent[B] =
    self match {
      case ChannelRead(msg)              => ChannelRead(f(msg))
      case ChannelRegistered             => ChannelRegistered
      case ChannelUnregistered           => ChannelUnregistered
      case event @ ExceptionCaught(_)    => event
      case event @ UserEventTriggered(_) => event
    }
}

object ChannelEvent {

  final case class ExceptionCaught(cause: Throwable) extends ChannelEvent[Nothing]
  final case class ChannelRead[A](message: A)        extends ChannelEvent[A]
  case class UserEventTriggered(event: UserEvent)    extends ChannelEvent[Nothing]
  case object ChannelRegistered                      extends ChannelEvent[Nothing]
  case object ChannelUnregistered                    extends ChannelEvent[Nothing]

  /**
   * Custom user-events that are triggered within ZIO Http
   */
  sealed trait UserEvent
  object UserEvent {
    case object HandshakeTimeout  extends UserEvent
    case object HandshakeComplete extends UserEvent
  }

  def channelRead[B](msg: B): ChannelEvent[B] =
    ChannelRead(msg)

  val channelRegistered: ChannelEvent[Nothing] =
    ChannelRegistered

  val channelUnregistered: ChannelEvent[Nothing] =
    ChannelUnregistered

  def exceptionCaught(cause: Throwable): ChannelEvent[Nothing] =
    ExceptionCaught(cause)

  def userEventTriggered(evt: UserEvent): ChannelEvent[Nothing] =
    UserEventTriggered(evt)
}
