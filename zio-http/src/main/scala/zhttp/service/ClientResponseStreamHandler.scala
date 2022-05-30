package zhttp.service
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpContent
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}

final class ClientResponseStreamHandler {
  private var onMessage: UnsafeContent => Unit = _
  private var holder: HttpContent              = _

  def init(ctx: ChannelHandlerContext, cb: UnsafeChannel => UnsafeContent => Unit): Unit = {
    onMessage = cb(new UnsafeChannel(ctx))
    if (holder != null)
      onMessage(new UnsafeContent(holder))
    holder = null
    ctx.read(): Unit
  }

  def update(msg: HttpContent): Unit = {
    if (onMessage == null) {
      holder = msg
    } else {
      onMessage(new UnsafeContent(msg))
    }
  }
}
