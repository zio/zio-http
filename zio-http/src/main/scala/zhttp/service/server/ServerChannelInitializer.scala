package zhttp.service.server

import zhttp.core._
import zhttp.service.Server.Settings
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.configureClearText

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
