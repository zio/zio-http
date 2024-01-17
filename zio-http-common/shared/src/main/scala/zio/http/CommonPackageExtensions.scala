package zio.http

import zio.http.codec.PathCodec

import java.util.UUID

trait CommonPackageExtensions {

  def boolean(name: String): PathCodec[Boolean] = PathCodec.bool(name)
  def int(name: String): PathCodec[Int]         = PathCodec.int(name)
  def long(name: String): PathCodec[Long]       = PathCodec.long(name)
  def string(name: String): PathCodec[String]   = PathCodec.string(name)
  val trailing: PathCodec[Path]                 = PathCodec.trailing
  def uuid(name: String): PathCodec[UUID]       = PathCodec.uuid(name)

  val Empty: Path = Path.empty
  val Root: Path  = Path.root

  /**
   * A channel that allows websocket frames to be written to it.
   */
  type WebSocketChannel = Channel[WebSocketChannelEvent, WebSocketChannelEvent]

  /**
   * A channel that allows websocket frames to be read and write to it.
   */
  type WebSocketChannelEvent = ChannelEvent[WebSocketFrame]
}
