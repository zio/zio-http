package zhttp.service.server
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler}
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.core.{JChannelHandler, JHttpObjectAggregator, JHttpServerCodec}
import zhttp.service.Server.Settings
import zhttp.service.{HTTP_KEEPALIVE_HANDLER, HTTP_REQUEST_HANDLER, OBJECT_AGGREGATOR, SERVER_CODEC_HANDLER}
final case class Http2OrHttpHandler[R](
  httpH: JChannelHandler,
  http2H: JChannelHandler,
  settings: Settings[R, Throwable],
) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      ctx.pipeline.addLast(Http2FrameCodecBuilder.forServer().build(), http2H)
      ()
    } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      ctx.pipeline.addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
        .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
        .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
        .addLast(HTTP_REQUEST_HANDLER, httpH)
      ()
    } else
      throw new IllegalStateException("unknown protocol: " + protocol)
  }
}
