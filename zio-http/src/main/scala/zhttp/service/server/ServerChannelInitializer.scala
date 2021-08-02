package zhttp.service.server

import io.netty.handler.codec.http.{HttpServerCodec, HttpServerKeepAliveHandler}
import zhttp.core._
import zhttp.service.Server.Settings
import zhttp.service.{SSL_HANDLER, UnsafeChannelExecutor}

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer[R](zExec: UnsafeChannelExecutor[R], settings: Settings[R, Throwable])
    extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {

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
      .addLast(settings.hApp.compile(zExec))
    ()
  }

}
