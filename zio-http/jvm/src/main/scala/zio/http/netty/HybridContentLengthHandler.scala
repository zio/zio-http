package zio.http.netty

import io.netty.channel._
import io.netty.handler.codec.http.HttpUtil.getContentLength
import io.netty.handler.codec.http._
import io.netty.handler.stream.ChunkedWriteHandler

class HybridContentLengthHandler(maxAggregatedLength: Int) extends ChannelInboundHandlerAdapter {
  var maxLength                                                        = maxAggregatedLength
  override def channelRead(ctx: ChannelHandlerContext, msg: Any): Unit = {
    msg match {
      case httpMessage: HttpMessage =>
        val contentLength = getContentLength(httpMessage, -1L)
        if (contentLength > maxAggregatedLength) {
          if (ctx.pipeline().get(classOf[HttpObjectAggregator]) != null) {
            ctx.pipeline().replace(classOf[HttpObjectAggregator], Names.ChunkedWriteHandler, new ChunkedWriteHandler())
          }
        } else {
          if (ctx.pipeline().get(classOf[ChunkedWriteHandler]) != null) {
            ctx
              .pipeline()
              .replace(classOf[ChunkedWriteHandler], Names.HttpObjectAggregator, new HttpObjectAggregator(maxLength))
          }
        }
      case _                        => // Ignore non-HTTP messages
    }
    ctx.fireChannelRead(msg): Unit
  }
}
