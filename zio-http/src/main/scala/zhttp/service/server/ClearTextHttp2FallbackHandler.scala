package zhttp.service.server

import io.netty.handler.codec.http.{
  HttpMessage => JHttpMessage,
  HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler,
}
import zhttp.core.{JChannelHandler, JChannelHandlerContext, JHttpObjectAggregator, JSimpleChannelInboundHandler}
import zhttp.service.Server.Settings
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR}

final case class ClearTextHttp2FallbackHandler[R](httpH: JChannelHandler, settings: Settings[R, Throwable])
    extends JSimpleChannelInboundHandler[JHttpMessage]() {
  @throws[Exception]
  override protected def channelRead0(ctx: JChannelHandlerContext, msg: JHttpMessage): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
    val pipeline = ctx.pipeline
    val thisCtx  = pipeline.context(this)
    pipeline
      .addAfter(thisCtx.name(), OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
      .addAfter(OBJECT_AGGREGATOR, HTTP_REQUEST_HANDLER, httpH)
      .replace(this, HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
    ctx.fireChannelRead(msg)
    ()
  }
}
