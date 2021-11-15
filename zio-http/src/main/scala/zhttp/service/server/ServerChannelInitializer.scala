package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearText

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](
  zExec: HttpRuntime[R],
  settings: Settings[R, Throwable],
  httpHandler: ChannelHandler,
  http2Handler: ChannelHandler,
) extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {

    val sslctx = ServerSSLHandler.build(settings.sslOption, settings.http2)
    if (sslctx != null) {
      channel
        .pipeline()
        .addLast(HTTP2_OR_HTTP_SERVER_HANDLER, Http2OrHttpServerHandler(httpHandler, http2Handler, settings))
        .addFirst(
          SSL_HANDLER,
          new OptionalSSLHandler(httpHandler, http2Handler, sslctx, settings),
        )
      ()
    } else configureClearText(httpHandler, http2Handler, channel, settings)
  }

}
