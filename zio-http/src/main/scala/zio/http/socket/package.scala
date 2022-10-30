package zio.http

package object socket {

  /**
   * A channel that allows websocket frames to be written to it.
   */
  type WebSocketChannel = ChannelForUserSocketApps[WebSocketFrame]

  /**
   * A channel that allows websocket frames to be read and write to it. // TODO Lying comment?
   */
  type WebSocketChannelEvent = ChannelEvent[WebSocketFrame, WebSocketFrame]
}
