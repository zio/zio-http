package zhttp.service.client

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpResponse}
import zhttp.service.{HTTP_RESPONSE_HANDLER, OBJECT_AGGREGATOR}

final case class ClientClearTextHttp2FallbackHandler(httpH: ChannelHandler)
  extends SimpleChannelInboundHandler[HttpResponse]() {
  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpResponse): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
    val pipeline = ctx.pipeline
    pipeline.addAfter(ctx.name(), OBJECT_AGGREGATOR,new HttpObjectAggregator(Int.MaxValue))
      .addAfter(OBJECT_AGGREGATOR,HTTP_RESPONSE_HANDLER,httpH)
    ctx.fireChannelRead(msg)
    ()
  }
}