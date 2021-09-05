package zhttp.service.server
import io.netty.channel.{ChannelHandler, ChannelHandlerContext => JChannelHandlerContext}
import io.netty.handler.codec.http.{
  HttpObjectAggregator,
  HttpServerCodec,
  HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler,
}
import io.netty.handler.codec.http2.{Http2FrameCodecBuilder => JHttp2FrameCodecBuilder}
import io.netty.handler.ssl.{
  ApplicationProtocolNames => JApplicationProtocolNames,
  ApplicationProtocolNegotiationHandler => JApplicationProtocolNegotiationHandler,
}
import zhttp.service.Server.Settings
import zhttp.service._
final case class Http2OrHttpHandler[R](
                                        httpH: ChannelHandler,
                                        http2H: ChannelHandler,
                                        settings: Settings[R, Throwable],
                                      ) extends JApplicationProtocolNegotiationHandler(JApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: JChannelHandlerContext, protocol: String): Unit = {
    if (JApplicationProtocolNames.HTTP_2 == protocol) {
      ctx.pipeline
        .addLast(HTTP2_SERVER_CODEC_HANDLER, JHttp2FrameCodecBuilder.forServer().build())
        .addLast(HTTP2_REQUEST_HANDLER, http2H)
      ()
    } else if (JApplicationProtocolNames.HTTP_1_1 == protocol) {
      ctx.pipeline
        .addLast(SERVER_CODEC_HANDLER, new HttpServerCodec)
        .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
        .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(settings.maxRequestSize))
        .addLast(HTTP_REQUEST_HANDLER, httpH)
      ()
    } else
      throw new IllegalStateException("unknown protocol: " + protocol)
  }
}