package zhttp.service.client

import example.client.Http2SettingsHandler
import io.netty.channel.{Channel, ChannelHandler, ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http.{FullHttpRequest, HttpClientCodec, HttpClientUpgradeHandler, HttpObjectAggregator}
import io.netty.handler.codec.http2.{Http2ClientUpgradeCodec, HttpToHttp2ConnectionHandler}
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

case object ClientChannelInitializerUtil {

  /**
   * Configure the pipeline for TLS NPN negotiation to HTTP/2.
   */
  def configureSsl(
    ch: Channel,
    settingsHandler: Http2SettingsHandler,
    connectionHandler: HttpToHttp2ConnectionHandler,
    sslOption: ClientSSLOptions,
    httpResponseHandler: ChannelHandler,
    http2ResponseHandler: Http2ClientResponseHandler,
    enableHttp2: Boolean,
    jReq: FullHttpRequest,
  ): Unit = {
    val pipeline = ch.pipeline
    if (enableHttp2) {
      pipeline.addFirst(SSL_HANDLER, ClientSSLHandler.ssl(sslOption, enableHttp2).newHandler(ch.alloc))
      pipeline.addFirst(new OptionalClientSSLHandler(httpResponseHandler))
      pipeline.addLast(
        HTTP2_OR_HTTP_CLIENT_HANDLER,
        Http2OrHttpClientHandler(
          ch,
          connectionHandler,
          settingsHandler,
          httpResponseHandler,
          http2ResponseHandler,
          jReq,
        ),
      )
      ()
    } else {
      pipeline.addFirst(SSL_HANDLER, ClientSSLHandler.ssl(sslOption, enableHttp2).newHandler(ch.alloc))
      pipeline
        .addLast(CLIENT_CODEC_HANDLER, new HttpClientCodec)
        .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
        .addLast(HTTP_RESPONSE_HANDLER, httpResponseHandler)
      ()
    }
  }

  /**
   * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.
   */
  def configureClearText(
    ch: Channel,
    settingsHandler: Http2SettingsHandler,
    connectionHandler: HttpToHttp2ConnectionHandler,
    httpResponseHandler: ChannelHandler,
    http2ResponseHandler: Http2ClientResponseHandler,
    enableHttp2: Boolean,
    jReq: FullHttpRequest,
  ): Unit = {
    val sourceCodec    = new HttpClientCodec
    val upgradeCodec   = new Http2ClientUpgradeCodec(connectionHandler)
    val upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536)
    ch.pipeline.addLast(CLIENT_CODEC_HANDLER, sourceCodec)
    if (enableHttp2) {
      ch.pipeline.addLast(CLIENT_CLEAR_TEXT_HTTP2_HANDLER, upgradeHandler)
      ch.pipeline.addLast(
        CLIENT_UPGRADE_REQUEST_HANDLER,
        new UpgradeRequestHandler(settingsHandler, http2ResponseHandler, jReq),
      )
      ch.pipeline()
        .addLast(CLIENT_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER, ClientClearTextHttp2FallbackHandler(httpResponseHandler))
      ()
    } else {
      ch.pipeline
        .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
        .addLast(HTTP_RESPONSE_HANDLER, httpResponseHandler)
      ch.pipeline.addLast(new UserEventLogger)
      ()
    }
  }

  /**
   * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
   */
  final private class UpgradeRequestHandler(
    settingsHandler: Http2SettingsHandler,
    http2ResponseHandler: Http2ClientResponseHandler,
    jReq: FullHttpRequest,
  ) extends ChannelInboundHandlerAdapter {
    @throws[Exception]
    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      ctx.writeAndFlush(jReq)
      ctx.fireChannelActive
      ctx.pipeline.addAfter(ctx.name(), HTTP2_SETTINGS_HANDLER, settingsHandler)
      ctx.pipeline().addAfter(HTTP2_SETTINGS_HANDLER, HTTP2_RESPONSE_HANDLER, http2ResponseHandler)
      ()
    }
  }

  /**
   * Class that logs any User Events triggered on this channel.
   */
  private class UserEventLogger extends ChannelInboundHandlerAdapter {
    @throws[Exception]
    override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit = {
      System.out.println("User Event Triggered: " + evt)
      ctx.fireUserEventTriggered(evt)
      ()
    }
  }
}
