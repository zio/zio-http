package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer, ChannelPipeline}
import io.netty.handler.codec.http.{HttpServerCodec, HttpServerExpectContinueHandler, HttpServerKeepAliveHandler}
import io.netty.handler.flow.FlowControlHandler
import zhttp.service.Server.Settings
import zhttp.service._

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](zExec: HttpRuntime[R], settings: Settings[R, Throwable])
    extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {

    val sslctx = if (settings.sslOption == null) null else settings.sslOption.sslContext
    if (sslctx != null) {
      channel
        .pipeline()
        .addFirst(
          SSL_HANDLER,
          new OptionalSSLHandler(sslctx, settings.sslOption.httpBehaviour),
        )
      ()
    }

    val ch: ChannelPipeline = channel
      .pipeline()
      .addLast(HTTP_SERVER_CODEC, new HttpServerCodec())               // TODO: See if server codec is really required
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler) // TODO: Make keep-alive configurable
      .addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())
      .addLast(HTTP_REQUEST_HANDLER, settings.app.compile(zExec))

    if (settings.acceptContinue)
      ch.addAfter(HTTP_SERVER_CODEC, HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())
    ()
  }

}
