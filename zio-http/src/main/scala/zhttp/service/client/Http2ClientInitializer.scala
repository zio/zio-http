package zhttp.service.client

import java.util.concurrent.{CountDownLatch, TimeUnit}

import example.client.Http2SettingsHandler
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2._
import io.netty.handler.logging.LogLevel.INFO
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */

case class Http2ClientInitializer(
  sslOption: ClientSSLOptions,
  httpResponseHandler: ChannelHandler,
  http2ResponseHandler: Http2ClientResponseHandler,
  scheme: String,
  enableHttp2: Boolean,
  jReq: FullHttpRequest,
  maxContentLength: Int = Int.MaxValue,
) extends ChannelInitializer[Channel] {
  var connectionHandler: HttpToHttp2ConnectionHandler = null
  var settingsHandler: Http2SettingsHandler           = null
  private val latch                                   = new CountDownLatch(1)
  var ccc                                             = null

  @throws[Exception]
  def awaitHandshake(timeout: Long, timeUnit: TimeUnit): Unit = {
    latch.await(timeout, timeUnit)
    ()
  }

  private val logger = new Http2FrameLogger(INFO, classOf[Http2ClientInitializer])

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

  @throws[Exception]
  override def initChannel(ch: Channel): Unit = {
    val connection = new DefaultHttp2Connection(false)
    connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
      .frameListener(
        new DelegatingDecompressorFrameListener(
          connection,
          new InboundHttp2ToHttpAdapterBuilder(connection)
            .maxContentLength(maxContentLength)
            .propagateSettings(true)
            .build(),
        ),
      )
      .frameLogger(logger)
      .connection(connection)
      .build()
    settingsHandler = new Http2SettingsHandler(ch.newPromise, jReq)
    if (scheme == "https") configureSsl(ch)
    else configureClearText(ch)
  }

  def getSettingsHandler: Http2SettingsHandler = settingsHandler

  /**
   * Configure the pipeline for TLS NPN negotiation to HTTP/2.
   */
  private def configureSsl(ch: Channel): Unit = {
    val pipeline = ch.pipeline
    pipeline.addLast(ClientSSLHandler.ssl(sslOption, enableHttp2).newHandler(ch.alloc))
    // We must wait for the handshake to finish and the protocol to be negotiated before configuring
    // the HTTP/2 components of the pipeline.
    if (enableHttp2) {
      pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {
          if (ApplicationProtocolNames.HTTP_2 == protocol) {
            val p = ctx.pipeline
            p.addLast(connectionHandler)
            p.addLast(settingsHandler, http2ResponseHandler)
            ()
          } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
            ch.pipeline.addLast(new HttpClientCodec, new HttpObjectAggregator(Int.MaxValue), httpResponseHandler)
            ()
          } else {
            throw new IllegalStateException("unknown protocol: " + protocol)

          }
        }
      })
      ()
    } else {
      ch
        .pipeline()
        .addLast(new HttpClientCodec)
        .addLast(new HttpObjectAggregator(Int.MaxValue))
        .addLast(httpResponseHandler)
      ()
    }
  }

  /**
   * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.
   */
  private def configureClearText(ch: Channel): Unit = {
    val sourceCodec    = new HttpClientCodec
    val upgradeCodec   = new Http2ClientUpgradeCodec(connectionHandler)
    val upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536)
    ch.pipeline.addLast(sourceCodec)
    if (enableHttp2) {
      ch.pipeline.addLast(upgradeHandler, new UpgradeRequestHandler)
      ch.pipeline().addLast(ClearTextHttp2FallbackClientHandler(httpResponseHandler))
    }
    else
      ch.pipeline
        .addLast(new HttpObjectAggregator(Int.MaxValue))
        .addLast(httpResponseHandler)
    ch.pipeline.addLast(new UserEventLogger)
    ()
  }

  /**
   * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
   */
  final private class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
    @throws[Exception]
    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      val upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/")
      ctx.writeAndFlush(upgradeRequest)
      ctx.fireChannelActive
      // Done with this handler, remove it from the pipeline.
      ctx.pipeline.remove(this)
      ctx.pipeline().addLast(settingsHandler, http2ResponseHandler)
      ()
    }
  }

}
