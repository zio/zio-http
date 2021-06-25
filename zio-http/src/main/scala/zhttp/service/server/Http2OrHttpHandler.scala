package zhttp.service.server
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerCodec, HttpServerKeepAliveHandler}
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.ssl.ApplicationProtocolNames
import io.netty.handler.ssl.ApplicationProtocolNegotiationHandler
import zhttp.core.JChannelHandler
import zhttp.service.Server.Settings
import zhttp.service.UnsafeChannelExecutor
final case class Http2OrHttpHandler[R](httpH: JChannelHandler,settings: Settings[R, Throwable],zExec: UnsafeChannelExecutor[R]) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      println("http2")
      ctx.pipeline.addLast(Http2FrameCodecBuilder.forServer().build(), new Http2Handler(zExec,settings) )
      ()
    }
    else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      println("http")
      ctx.pipeline.addLast(new HttpServerCodec, new HttpServerKeepAliveHandler, new HttpObjectAggregator(settings.maxRequestSize), httpH)
      ()
    }
    else
      throw new IllegalStateException("unknown protocol: " + protocol)
  }
}