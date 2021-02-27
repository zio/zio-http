package zio-http.domain

import zio.URIO

package object netty {
  type ChannelEvent[+C, +A]     = (C, Event[A])
  type ChannelOperation[+C, +A] = (C, Operation[A])
  type EventHandler[-R, -A, +B] = Event[A] => URIO[R, Operation[B]]
}
