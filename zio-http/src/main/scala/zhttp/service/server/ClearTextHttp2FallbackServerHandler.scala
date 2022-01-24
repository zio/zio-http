package zhttp.service.server

import io.netty.channel.{ChannelHandler, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpMessage, HttpServerKeepAliveHandler}
import io.netty.handler.flow.FlowControlHandler
import zhttp.service.Server.Config
import zhttp.service.{
  FLOW_CONTROL_HANDLER,
  HTTP_KEEPALIVE_HANDLER,
  HTTP_SERVER_REQUEST_HANDLER,
  HTTP_SERVER_RESPONSE_HANDLER,
}

final case class ClearTextHttp2FallbackServerHandler[R](
  reqHandler: ChannelHandler,
  respHandler: ChannelHandler,
  settings: Config[R, Throwable],
) extends SimpleChannelInboundHandler[HttpMessage]() {
  @throws[Exception]
  override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
    val pipeline = ctx.pipeline
    val thisCtx  = pipeline.context(this)
    pipeline
      .addAfter(thisCtx.name(), FLOW_CONTROL_HANDLER, new FlowControlHandler())
      .addAfter(FLOW_CONTROL_HANDLER, HTTP_SERVER_REQUEST_HANDLER, reqHandler)
      .addAfter(HTTP_SERVER_REQUEST_HANDLER, HTTP_SERVER_RESPONSE_HANDLER, respHandler)
      .replace(this, HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
    ctx.fireChannelRead(msg)
    // TODO: Update the list of handlers
    ()
  }
}
