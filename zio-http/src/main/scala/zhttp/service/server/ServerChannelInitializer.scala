package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelInitializer}
import io.netty.handler.codec.http.{HttpServerCodec, HttpServerExpectContinueHandler, HttpServerKeepAliveHandler}
import zhttp.service.Server.Settings
import zhttp.service._

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](zExec: HttpRuntime[R], settings: Settings[R, Throwable])
    extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {
    // !! IMPORTANT !!
    // Order of handlers are critical to make this work
    val pipeline = channel.pipeline()

    // SSL
    // Add SSL Handler if CTX is available
    val sslctx = if (settings.sslOption == null) null else settings.sslOption.sslContext
    if (sslctx != null) pipeline.addFirst(SSL_HANDLER, new OptionalSSLHandler(sslctx, settings.sslOption.httpBehaviour))

    // ServerCodec
    // Always add ServerCodec
    pipeline.addLast(HTTP_SERVER_CODEC, new HttpServerCodec()) // TODO: See if server codec is really required

    // ExpectContinueHandler
    // Add expect continue handler is settings is true
    if (settings.acceptContinue) pipeline.addLast(HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())

    // KeepAliveHandler
    // Add Keep-Alive handler is settings is true
    if (settings.keepAlive) pipeline.addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(HTTP_REQUEST_HANDLER, settings.app.compile(zExec, settings))

    ()
  }

}
