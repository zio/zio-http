package zhttp.service
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zhttp.http.HttpData.{UnsafeChannel, UnsafeContent}
import zhttp.logging.{LogLevel, Logger}

final class RequestBodyHandler(val callback: UnsafeChannel => UnsafeContent => Unit)
    extends SimpleChannelInboundHandler[HttpContent](false) { self =>

  private val log = Logger.console.withLevel(LogLevel.Trace) // TODO: loglevel should come from server config object

  private val tags = List("zhttp")

  private var onMessage: UnsafeContent => Unit = _

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    log.trace(s"Handling content: $msg", tags)
    self.onMessage(new UnsafeContent(msg))
    if (msg.isInstanceOf[LastHttpContent]) {
      ctx.channel().pipeline().remove(self): Unit
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    log.trace(s"RequestBodyHandler as been added to the channel pipeline.", tags)
    self.onMessage = callback(new UnsafeChannel(ctx))
    ctx.read(): Unit
  }
}
