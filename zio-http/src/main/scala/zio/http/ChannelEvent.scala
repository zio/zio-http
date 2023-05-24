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
 * netty channel. `A` represents the message type.
 */
sealed trait ChannelEvent[+A] { self =>
  import ChannelEvent._

  def map[B](f: A => B): ChannelEvent[B] =
    self match {
      case Read(msg)                     => Read(f(msg))
      case Registered                    => Registered
      case Unregistered                  => Unregistered
      case event @ ExceptionCaught(_)    => event
      case event @ UserEventTriggered(_) => event
    }
}

object ChannelEvent {

  final case class ExceptionCaught(cause: Throwable) extends ChannelEvent[Nothing]
  final case class Read[A](message: A)               extends ChannelEvent[A]
  case class UserEventTriggered(event: UserEvent)    extends ChannelEvent[Nothing]
  case object Registered                             extends ChannelEvent[Nothing]
  case object Unregistered                           extends ChannelEvent[Nothing]

  /**
   * Custom user-events that are triggered within ZIO Http
   */
  sealed trait UserEvent
  object UserEvent {
    case object HandshakeTimeout  extends UserEvent
    case object HandshakeComplete extends UserEvent
  }

  def read[A](msg: A): ChannelEvent[A] =
    Read(msg)

  val registered: ChannelEvent[Nothing] =
    Registered

  val unregistered: ChannelEvent[Nothing] =
    Unregistered

  def exceptionCaught(cause: Throwable): ChannelEvent[Nothing] =
    ExceptionCaught(cause)

  def userEventTriggered(evt: UserEvent): ChannelEvent[Nothing] =
    UserEventTriggered(evt)
}
