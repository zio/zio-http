package zhttp.service.server

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory
import io.netty.handler.codec.http.{HttpMessage, HttpServerCodec, HttpServerKeepAliveHandler, HttpServerUpgradeHandler}
import io.netty.handler.codec.http2.{Http2CodecUtil, Http2FrameCodecBuilder, Http2ServerUpgradeCodec}
import io.netty.util.AsciiString
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

  //  {
  //    if (settings.enableHttp2 == false) {
  //      val sslctx = if (settings.sslOption == null) null else settings.sslOption.sslContext
  //      if (sslctx != null) {
  //        channel
  //          .pipeline()
  //          .addFirst(
  //            SSL_HANDLER,
  //            new OptionalSSLHandler(
  //              sslctx
  //                .applicationProtocolConfig(
  //                  new ApplicationProtocolConfig(
  //                    Protocol.ALPN,
  //                    SelectorFailureBehavior.NO_ADVERTISE,
  //                    SelectedListenerFailureBehavior.ACCEPT,
  //                    ApplicationProtocolNames.HTTP_1_1,
  //                  ),
  //                )
  //                .build(),
  //              settings.sslOption.httpBehaviour,
  //            ),
  //          )
  //        ()
  //      }
  //      channel
  //        .pipeline()
  //        .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
  //        .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
  //        .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
  //        .addLast(HTTP_REQUEST_HANDLER, httpH)
  //      ()
  //    }
  //    else
  //    {
  //      val sslctx = if (settings.sslOption == null) null else settings.sslOption.sslContext
  //      if (sslctx != null) {
  //        channel
  //          .pipeline()
  //          .addLast(
  //            sslctx
  //              .ciphers(Http2SecurityUtil.CIPHERS, SupportedCipherSuiteFilter.INSTANCE)
  //              .applicationProtocolConfig(
  //                new ApplicationProtocolConfig(
  //                  Protocol.ALPN,
  //                  SelectorFailureBehavior.NO_ADVERTISE,
  //                  SelectedListenerFailureBehavior.ACCEPT,
  //                  ApplicationProtocolNames.HTTP_2,
  //                  ApplicationProtocolNames.HTTP_1_1,
  //                ),
  //              )
  //              .build()
  //              .newHandler(channel.alloc()),
  //            Http2FrameCodecBuilder.forServer().build(), http2H,
  //          )
  //        ()
  //      } else
  //      {
  //
  //        val p           = channel.pipeline
  //        val sourceCodec = new HttpServerCodec
  //        p.addLast(SERVER_CODEC_HANDLER,sourceCodec)
  //        p.addLast(CLEAR_TEXT_HTTP2_HANDLER,new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory))
  //        p.addLast(CLEAR_TEXT_HTTP2_FALLBACK_HANDLER,new SimpleChannelInboundHandler[HttpMessage]() {
  //          @throws[Exception]
  //          override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
  //            System.err.println("Directly talking: " + msg.protocolVersion + " (no upgrade was attempted)")
  //            val pipeline = ctx.pipeline
  //            val thisCtx  = pipeline.context(this)
  //            pipeline
  //              .addAfter(thisCtx.name(), OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
  //              .addAfter(OBJECT_AGGREGATOR, HTTP_REQUEST_HANDLER, httpH)
  //              .replace(this, HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
  //            ctx.fireChannelRead(msg)
  //            ()
  //          }
  //        })
  //        ()
  //      }
  //
  //    }
  //
  //  }
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
    val sourceCodec = new HttpServerCodec
    p.addLast(EncryptedMessageFilter(httpH, settings))
    p.addLast(SERVER_CODEC_HANDLER, sourceCodec)
    p.addLast(CLEAR_TEXT_HTTP2_HANDLER, new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2H)))
    p.addLast(
      CLEAR_TEXT_HTTP2_FALLBACK_HANDLER,
      new SimpleChannelInboundHandler[HttpMessage]() {
        @throws[Exception]
        override protected def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = { // If this handler is hit then no upgrade has been attempted and the client is just talking HTTP.
          System.err.println("Directly talking: " + msg.protocolVersion + " (no upgrade was attempted)")
          val pipeline = ctx.pipeline
          val thisCtx  = pipeline.context(this)
          pipeline
            .addAfter(thisCtx.name(), OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
            .addAfter(OBJECT_AGGREGATOR, HTTP_REQUEST_HANDLER, httpH)
            .replace(this, HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
          ctx.fireChannelRead(msg)
          ()
        }
      },
    )
    ()
  } else {
    c.pipeline()
      .addLast(SERVER_CODEC_HANDLER, new JHttpServerCodec)
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }

  private def upgradeCodecFactory(http2H: JChannelHandler): UpgradeCodecFactory = {
    new HttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): Http2ServerUpgradeCodec = {
        if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer.build, http2H)
        else null
      }
    }
  }
}
