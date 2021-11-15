package zhttp.service.server
import io.netty.channel.{ChannelHandler, ChannelHandlerContext => JChannelHandlerContext}
import io.netty.handler.codec.http.{HttpServerCodec, HttpServerExpectContinueHandler, HttpServerKeepAliveHandler}
import io.netty.handler.codec.http2.Http2FrameCodecBuilder
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.service.Server.Settings
import zhttp.service._
final case class Http2OrHttpServerHandler[R](
  httpH: ChannelHandler,
  http2H: ChannelHandler,
  settings: Settings[R, Throwable],
) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  @throws[Exception]
  override protected def configurePipeline(ctx: JChannelHandlerContext, protocol: String): Unit = {
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      ctx.pipeline
        .addLast(HTTP2_SERVER_CODEC_HANDLER, Http2FrameCodecBuilder.forServer().build())
        .addLast(HTTP2_REQUEST_HANDLER, http2H)
      ()
    } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      ctx.pipeline
        .addLast(HTTP_SERVER_CODEC, new HttpServerCodec)
        .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
        .addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())
        .addLast(HTTP_REQUEST_HANDLER, httpH)
      ()
    } else
      throw new IllegalStateException("unknown protocol: " + protocol)

    if (settings.acceptContinue) {
      ctx.pipeline().addAfter(HTTP_SERVER_CODEC, HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())
      ()
    }
  }
}
