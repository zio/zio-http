package zhttp.service.server
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec, HttpServerKeepAliveHandler}
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.core.JChannelHandler
import zhttp.service.Server.Settings
final case class Http2OrHttpHandler[R](httpH: JChannelHandler,http2H: JChannelHandler,settings: Settings[R, Throwable]) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      ctx.pipeline.addLast(Http2FrameCodecBuilder.forServer().build(), http2H )
      ()
    }
    else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      ctx.pipeline.addLast(new HttpServerCodec, new HttpServerKeepAliveHandler, new HttpObjectAggregator(settings.maxRequestSize), httpH)
      ()
    }
    else
      throw new IllegalStateException("unknown protocol: " + protocol)
  }
}