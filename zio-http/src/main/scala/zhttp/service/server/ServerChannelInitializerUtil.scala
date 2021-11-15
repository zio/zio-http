package zhttp.service.server

import io.netty.channel.{Channel, ChannelHandler}
import io.netty.handler.codec.http.HttpServerUpgradeHandler.{UpgradeCodecFactory => JUpgradeCodecFactory}
import io.netty.handler.codec.http.{
  HttpServerCodec,
  HttpServerExpectContinueHandler,
  HttpServerKeepAliveHandler,
  HttpServerUpgradeHandler,
}
import io.netty.handler.codec.http2.{Http2CodecUtil, Http2FrameCodecBuilder, Http2ServerUpgradeCodec}
import io.netty.handler.flow.FlowControlHandler
import io.netty.util.{AsciiString => JAsciiString}
import zhttp.service.Server.Settings
import zhttp.service._

case object ServerChannelInitializerUtil {

  private def upgradeCodecFactory(http2H: ChannelHandler): JUpgradeCodecFactory = {
    new HttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): Http2ServerUpgradeCodec = {
        if (JAsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer.build, http2H)
        else null
      }
    }
  }

  def configureClearText[R](
    httpH: ChannelHandler,
    http2H: ChannelHandler,
    c: Channel,
    settings: Settings[R, Throwable],
  ) = {
    if (settings.http2) {
      val p           = c.pipeline
      val sourceCodec = new HttpServerCodec
      p.addLast(ENCRYPTION_FILTER_HANDLER, EncryptedMessageFilter(httpH, settings))
      p.addLast(HTTP_SERVER_CODEC, sourceCodec)
      p.addLast(SERVER_CLEAR_TEXT_HTTP2_HANDLER, new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2H)))
      p.addLast(SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER, ClearTextHttp2FallbackServerHandler(httpH, settings))
      ()
    } else {
      c.pipeline()
        .addLast(HTTP_SERVER_CODEC, new HttpServerCodec)
        .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
        .addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())
        .addLast(HTTP_REQUEST_HANDLER, httpH)
      ()
    }
    if (settings.acceptContinue) {
      c.pipeline().addAfter(HTTP_SERVER_CODEC, HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())
      ()
    } else ()
  }
}
