package zhttp.service.server

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{
  HttpMessage,
  HttpObjectAggregator,
  HttpServerExpectContinueHandler,
  HttpServerKeepAliveHandler,
}
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.flush.FlushConsolidationHandler
import zhttp.service.Server.Config
import zhttp.service._

final case class ClearTextHttp2FallbackServerHandler(
  reqHandler: ChannelHandler,
  cfg: Config[_, Throwable],
) extends SimpleChannelInboundHandler[HttpMessage]() {
  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = {
    // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
    val pipeline = ctx.pipeline
    // ObjectAggregator
    // Always add ObjectAggregator
    if (cfg.useAggregator)
      pipeline.addLast(HTTP_SERVER_OBJECT_AGGREGATOR, new HttpObjectAggregator(cfg.objectAggregator))

    // ExpectContinueHandler
    // Add expect continue handler is settings is true
    if (cfg.acceptContinue) pipeline.addLast(HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())

    // KeepAliveHandler
    // Add Keep-Alive handler is settings is true
    if (cfg.keepAlive) pipeline.addLast(HTTP_SERVER_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)

    // FlowControlHandler
    // Required because HttpObjectDecoder fires an HttpRequest that is immediately followed by a LastHttpContent event.
    // For reference: https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html
    if (cfg.flowControl) pipeline.addLast(SERVER_FLOW_CONTROL_HANDLER, new FlowControlHandler())

    // FlushConsolidationHandler
    // Flushing content is done in batches. Can potentially improve performance.
    if (cfg.consolidateFlush) pipeline.addLast(HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER, new FlushConsolidationHandler)

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(HTTP_SERVER_REQUEST_HANDLER, reqHandler)
    ctx.fireChannelRead(msg)
    ()
  }
}
