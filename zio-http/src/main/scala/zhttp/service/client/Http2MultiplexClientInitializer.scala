
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

case class Http2MultiplexClientInitializer(
                                   sslOption: ClientSSLOptions,
                                          scheme:String,
                                   http2ResponseHandler: Http2ClientResponseHandler,
                                   maxContentLength: Int = Int.MaxValue,
                                 ) extends ChannelInitializer[Channel] {
  var connectionHandler: HttpToHttp2ConnectionHandler = null
  var settingsHandler: Http2SettingsHandler           = null
  var sp:ChannelPromise =null
  private val latch                                   = new CountDownLatch(1)

  @throws[Exception]
  def awaitHandshake(timeout: Long, timeUnit: TimeUnit): Unit = {
    latch.await(timeout, timeUnit)
    ()
  }

  private val logger = new Http2FrameLogger(INFO, classOf[Http2ClientInitializer])

  /**
   * Class that logs any User Events triggered on this channel.
   */


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
    sp= ch.newPromise()
    settingsHandler = new Http2SettingsHandler(sp, None,scheme)
    if (scheme == "https") configureSsl(ch)
    else configureClearText(ch)
  }

  def getSettingsHandler: Http2SettingsHandler = settingsHandler

  /**
   * Configure the pipeline for TLS NPN negotiation to HTTP/2.
   */
  private def configureSsl(ch: Channel): Unit = {
    println("client is trying ssl!")
    val pipeline = ch.pipeline
    pipeline.addLast(ClientSSLHandler.ssl(sslOption, true).newHandler(ch.alloc))
    // We must wait for the handshake to finish and the protocol to be negotiated before configuring
    // the HTTP/2 components of the pipeline.
      println("client is trying http2")
      pipeline.addLast(new ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
        override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {
          if (ApplicationProtocolNames.HTTP_2 == protocol) {

            pipeline.addLast(connectionHandler)
            pipeline.addLast(settingsHandler, http2ResponseHandler)
            println("server accepted http2")
            ()
          } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
            sp.setFailure( new RuntimeException ("Server doesn't support http2"))
            ()
          } else {
            throw new IllegalStateException("unknown protocol: " + protocol)
          }
        }
      })
      ()
  }

  /**
   * Configure the pipeline for a cleartext upgrade from HTTP to HTTP/2.
   */
  private def configureClearText(ch: Channel): Unit = {
    println("client is not trying ssl")
    val sourceCodec    = new HttpClientCodec
    val upgradeCodec   = new Http2ClientUpgradeCodec(connectionHandler)
    val upgradeHandler = new HttpClientUpgradeHandler(sourceCodec, upgradeCodec, 65536)
    ch.pipeline.addLast(sourceCodec)
      println("client is trying http2")
      ch.pipeline.addLast(upgradeHandler, new UpgradeRequestHandler)
      ch.pipeline().addLast(MultiplexClearTextFallback(sp))
      ()

  }

  /**
   * A handler that triggers the cleartext upgrade to HTTP/2 by sending an initial HTTP request.
   */
  final private class UpgradeRequestHandler extends ChannelInboundHandlerAdapter {
    @throws[Exception]
    override def channelActive(ctx: ChannelHandlerContext): Unit = {
      //      val upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.HEAD, "/")
      println("as part of tyring to make http2 connection client is sending the request from upgrade handler")
      println("handlers before upgrade request")
      println(ctx.pipeline().names())
      import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpMethod, HttpVersion}
      val upgradeRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      ctx.writeAndFlush(upgradeRequest)
      ctx.fireChannelActive
      // Done with this handler, remove it from the pipeline.
      ctx.pipeline.addAfter(ctx.name() ,"setting", settingsHandler)
      ctx.pipeline().addAfter("setting", "http2",http2ResponseHandler)
      println("handlers after upgrade request")
      println(ctx.pipeline().names())
      ()
    }
  }


}
