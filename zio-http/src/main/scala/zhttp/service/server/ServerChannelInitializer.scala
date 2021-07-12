package zhttp.service.server

import io.netty.channel.{
  ChannelHandlerContext => JChannelHandlerContext,
  SimpleChannelInboundHandler => JSimpleChannelInboundHandler,
}
import io.netty.handler.codec.http.HttpServerUpgradeHandler.{UpgradeCodecFactory => JUpgradeCodecFactory}
import io.netty.handler.codec.http.{
  HttpMessage => JHttpMessage,
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
import zhttp.core._
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerChannelInitializer.configureClearText

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer[R](
  httpH: JChannelHandler,
  http2H: JChannelHandler,
  settings: Settings[R, Throwable],
) extends JChannelInitializer[JChannel] {

  override def initChannel(channel: JChannel): Unit = {
    val sslctx = ServerSSLHandler.build(settings.sslOption, settings.enableHttp2)
    if (sslctx != null) {
      channel
        .pipeline()
        .addLast(HTTP2_OR_HTTP_HANDLER, Http2OrHttpHandler(httpH, http2H, settings))
        .addFirst(SSL_HANDLER, new OptionalSSLHandler(httpH, http2H, sslctx, settings))
      ()
    } else configureClearText(httpH, http2H, channel, settings)
  }
}

object ServerChannelInitializer {
  def configureClearText[R](
    httpH: JChannelHandler,
    http2H: JChannelHandler,
    c: JChannel,
    settings: Settings[R, Throwable],
  ) = if (settings.enableHttp2) {

    //TODO: add an encryptedmessagefilter
    val p           = c.pipeline
    val sourceCodec = new JHttpServerCodec
    p.addLast(EncryptedMessageFilter(httpH, settings))
    p.addLast(SERVER_CODEC_HANDLER, sourceCodec)
    p.addLast(CLEAR_TEXT_HTTP2_HANDLER, new JHttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2H)))
    p.addLast(
      CLEAR_TEXT_HTTP2_FALLBACK_HANDLER,
      new JSimpleChannelInboundHandler[JHttpMessage]() {
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
      },
    )
    ()
  } else {
    c.pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
  private def upgradeCodecFactory(http2H: JChannelHandler): JUpgradeCodecFactory = {
    new JHttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): JHttp2ServerUpgradeCodec = {
        if (JAsciiString.contentEquals(JHttp2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new JHttp2ServerUpgradeCodec(JHttp2FrameCodecBuilder.forServer.build, http2H)
        else null
      }
    }
  }
}
