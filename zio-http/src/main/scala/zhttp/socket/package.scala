package zhttp

package object socket {
  type RSocket[-R, -A, +B]  = Socket[R, Throwable, A, B]
  type TaskSocket[-A, +B]   = Socket[Any, Throwable, A, B]
  type URSocket[-R, -A, +B] = Socket[R, Nothing, A, B]
  type USocket[-A, +B]      = Socket[Any, Nothing, A, B]
  type WebSocket[-R, +E]    = Socket[R, E, WebSocketFrame, WebSocketFrame]
  type UWebSocket           = Socket[Any, Nothing, WebSocketFrame, WebSocketFrame]
}
