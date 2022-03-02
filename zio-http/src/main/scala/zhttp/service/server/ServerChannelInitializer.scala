package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import zhttp.service.Server.Config
import zhttp.service._
import zhttp.service.server.ServerChannelInitializerUtil.{configureClearTextHttp1, configureClearTextHttp2}

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](
  zExec: HttpRuntime[R],
  cfg: Config[R, Throwable],
  reqHandler: ChannelHandler,
  http2ReqHandler: ChannelHandler,
  http2ResHandler: ChannelHandler,
) extends ChannelInitializer[Channel] {
  val sslctx = if (cfg.sslOption == null) null else ServerSSLBuilder.build(cfg.sslOption.sslContextBuilder, cfg.http2)

  override def initChannel(channel: Channel): Unit = {
    if (!cfg.http2) {
      // !! IMPORTANT !!
      // Order of handlers are critical to make this work
      val pipeline = channel.pipeline()

      // SSL
      // Add SSL Handler if CTX is available
      if (sslctx != null)
        pipeline
          .addFirst(
            SERVER_SSL_HANDLER,
            new OptionalServerSSLHandler(sslctx, cfg, reqHandler, http2ReqHandler, http2ResHandler),
          )
      configureClearTextHttp1(cfg, reqHandler, pipeline)
      ()
    } else {
      if (sslctx != null) {
        channel
          .pipeline()
          .addFirst(
            SERVER_SSL_HANDLER,
            new OptionalServerSSLHandler(sslctx, cfg, reqHandler, http2ReqHandler, http2ResHandler),
          )
          .addLast(
            HTTP2_OR_HTTP_SERVER_HANDLER,
            Http2OrHttpServerHandler(reqHandler, http2ReqHandler, http2ResHandler, cfg),
          )
        ()
      } else {
        configureClearTextHttp2(reqHandler, http2ReqHandler, http2ResHandler, channel, cfg, true)
      }
    }
    if (cfg.channelInitializer != null) { cfg.channelInitializer(channel.pipeline) }
    ()
  }
}
