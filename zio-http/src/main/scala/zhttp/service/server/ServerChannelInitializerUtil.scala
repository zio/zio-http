package zhttp.service.server

import io.netty.handler.codec.http.HttpServerUpgradeHandler.{UpgradeCodecFactory => JUpgradeCodecFactory}
import io.netty.handler.codec.http.{
  HttpServerCodec => JHttpServerCodec,
  HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler,
  HttpServerUpgradeHandler => JHttpServerUpgradeHandler,
}
import io.netty.handler.codec.http2.{
  Http2CodecUtil => JHttp2CodecUtil,
  Http2FrameCodecBuilder => JHttp2FrameCodecBuilder,
  Http2ServerUpgradeCodec => JHttp2ServerUpgradeCodec,
}
import io.netty.util.{AsciiString => JAsciiString}
import zhttp.core.{JChannel, JChannelHandler, JHttpObjectAggregator}
import zhttp.service.Server.Settings
import zhttp.service._

case object ServerChannelInitializerUtil {

  private def upgradeCodecFactory(http2H: JChannelHandler): JUpgradeCodecFactory = {
    new JHttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): JHttp2ServerUpgradeCodec = {
        if (JAsciiString.contentEquals(JHttp2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new JHttp2ServerUpgradeCodec(JHttp2FrameCodecBuilder.forServer.build, http2H)
        else null
      }
    }
  }

  def configureClearText[R](
    httpH: JChannelHandler,
    http2H: JChannelHandler,
    c: JChannel,
    settings: Settings[R, Throwable],
  ) = if (settings.enableHttp2) {
    val p           = c.pipeline
    val sourceCodec = new JHttpServerCodec
    p.addLast(ENCRYPTION_FILTER_HANDLER, EncryptedMessageFilter(httpH, settings))
    p.addLast(SERVER_CODEC_HANDLER, sourceCodec)
    p.addLast(CLEAR_TEXT_HTTP2_HANDLER, new JHttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2H)))
    p.addLast(CLEAR_TEXT_HTTP2_FALLBACK_HANDLER, ClearTextHttp2FallbackHandler(httpH, settings))
    ()
  } else {
    c.pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
}
