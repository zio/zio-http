package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{HttpServerCodec, HttpServerKeepAliveHandler}
import zhttp.service.Server.Settings
import zhttp.service.{HTTP_HANDLER, HttpRuntime, SSL_HANDLER}

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
    channel
      .pipeline()
      .addLast(new HttpServerCodec())          // TODO: See if server codec is really required
      .addLast(new HttpServerKeepAliveHandler) // TODO: Make keep-alive configurable
      .addLast(HTTP_HANDLER, settings.endpoint.compile(zExec))

    ()
  }

}
