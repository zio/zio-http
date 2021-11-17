package zhttp.service.client

import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2._
import io.netty.handler.logging.LogLevel.INFO
import zhttp.service.client.ClientChannelInitializerUtil._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions

import java.util.concurrent.{CountDownLatch, TimeUnit}

/**
 * Configures the client pipeline to support HTTP/2 frames.
 */

case class ClientChannelInitializer(
  sslOption: ClientSSLOptions,
  httpResponseHandler: ChannelHandler,
  http2ResponseHandler: Http2ClientResponseHandler,
  scheme: String,
  enableHttp2: Boolean,
  jReq: FullHttpRequest,
  maxContentLength: Int = Int.MaxValue,
) extends ChannelInitializer[Channel] {
  private val latch = new CountDownLatch(1)

  @throws[Exception]
  def awaitHandshake(timeout: Long, timeUnit: TimeUnit): Unit = {
    latch.await(timeout, timeUnit)
    ()
  }

  private val logger = new Http2FrameLogger(INFO, classOf[ClientChannelInitializer])

  @throws[Exception]
  override def initChannel(ch: Channel): Unit = {
    val connection        = new DefaultHttp2Connection(false)
    val connectionHandler = new HttpToHttp2ConnectionHandlerBuilder()
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
    val settingsHandler   = Http2SettingsHandler(ch.newPromise, jReq, scheme)
    if (scheme == "https")
      configureSsl(
        ch,
        settingsHandler,
        connectionHandler,
        sslOption,
        httpResponseHandler,
        http2ResponseHandler,
        enableHttp2,
        jReq,
      )
    else
      configureClearText(
        ch,
        settingsHandler,
        connectionHandler,
        httpResponseHandler,
        http2ResponseHandler,
        enableHttp2,
        jReq,
      )
  }

}
