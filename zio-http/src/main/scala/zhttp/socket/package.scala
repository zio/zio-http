package zhttp

import zhttp.service.{Channel, ChannelEvent}

package object socket {
  type WebSocketChannel      = Channel[WebSocketFrame]
  type WebSocketChannelEvent = ChannelEvent[WebSocketFrame, WebSocketFrame]
  type WebSocketEvent        = ChannelEvent.Event[WebSocketFrame]
}
