package zhttp.service
import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}
import zhttp.http.Body
import zio.Chunk

final class ContentHandler(val async: Body.UnsafeAsync) extends SimpleChannelInboundHandler[HttpContent](true) { self =>

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpContent): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))
    async(ctx.channel(), chunk, isLast)
    if (isLast) ctx.channel().pipeline().remove(self): Unit
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    ctx.read(): Unit
  }
}
