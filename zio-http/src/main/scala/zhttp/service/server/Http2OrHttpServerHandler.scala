package zhttp.service.server
import io.netty.channel.{ChannelHandler, ChannelHandlerContext => JChannelHandlerContext}
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.service.Server.Config
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearTextHttp1
final case class Http2OrHttpServerHandler(
                                           reqHandler: ChannelHandler,
                                           respHandler: ChannelHandler,
                                           http2Handler: ChannelHandler,
                                           cfg: Config[_, Throwable],
                                         ) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: JChannelHandlerContext, protocol: String): Unit = {
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      ctx.pipeline
        .addLast(HTTP2_SERVER_CODEC_HANDLER, Http2FrameCodecBuilder.forServer().build())
        .addLast(HTTP2_REQUEST_HANDLER, http2Handler)
      ()
    } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      configureClearTextHttp1(cfg, reqHandler, respHandler, ctx.pipeline())
      ()
    } else
      throw new IllegalStateException("unknown protocol: " + protocol)
  }
}