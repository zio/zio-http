package zhttp

import zhttp.http._

package object channel {
  type UOperation[+A]                    = Operation[A, Unit]
  type HttpDataChannel[-R, +E, S]        = HttpChannel[R, E, S, S]
  type HttpChannelHandler[-R, +E, -A, B] = Http[R, E, A, HttpDataChannel[R, E, B]]
  type UHttpChannelHandler[-A, B]        = HttpChannelHandler[Any, Nothing, A, B]
}
