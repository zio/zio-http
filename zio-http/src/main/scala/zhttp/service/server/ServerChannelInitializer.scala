package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import io.netty.handler.codec.http.HttpObjectDecoder.{
  DEFAULT_MAX_CHUNK_SIZE,
  DEFAULT_MAX_HEADER_SIZE,
  DEFAULT_MAX_INITIAL_LINE_LENGTH,
}
import io.netty.handler.codec.http._
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.logging.LoggingHandler
import zhttp.logging.LogLevel
import zhttp.service.Server.Config
import zhttp.service._
import zhttp.service.server.LogLevelTransform._
import zhttp.service.server.ServerChannelInitializer.log

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](
  zExec: HttpRuntime[R],
  cfg: Config[R, Throwable],
  reqHandler: ChannelHandler,
) extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {
    // !! IMPORTANT !!
    // Order of handlers are critical to make this work
    val pipeline = channel.pipeline()
    log.debug(s"Connection initialized: ${channel.remoteAddress()}")
    // SSL
    // Add SSL Handler if CTX is available
    val sslctx   = if (cfg.sslOption == null) null else cfg.sslOption.sslContext
    if (sslctx != null)
      pipeline
        .addFirst(SSL_HANDLER, new OptionalSSLHandler(sslctx, cfg.sslOption.httpBehaviour, cfg))

    // ServerCodec
    // Instead of ServerCodec, we should use Decoder and Encoder separately to have more granular control over performance.
    pipeline.addLast(
      "decoder",
      new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE, false),
    )
    pipeline.addLast("encoder", new HttpResponseEncoder())

    // HttpContentDecompressor
    if (cfg.requestDecompression._1)
      pipeline.addLast(HTTP_REQUEST_DECOMPRESSION, new HttpContentDecompressor(cfg.requestDecompression._2))

    // TODO: See if server codec is really required

    // ObjectAggregator
    // Always add ObjectAggregator
    if (cfg.useAggregator)
      pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(cfg.objectAggregator))

    // ExpectContinueHandler
    // Add expect continue handler is settings is true
    if (cfg.acceptContinue) pipeline.addLast(HTTP_SERVER_EXPECT_CONTINUE, new HttpServerExpectContinueHandler())

    // KeepAliveHandler
    // Add Keep-Alive handler is settings is true
    if (cfg.keepAlive) pipeline.addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)

    // FlowControlHandler
    // Required because HttpObjectDecoder fires an HttpRequest that is immediately followed by a LastHttpContent event.
    // For reference: https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html
    if (cfg.flowControl) pipeline.addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())

    // FlushConsolidationHandler
    // Flushing content is done in batches. Can potentially improve performance.
    if (cfg.consolidateFlush) pipeline.addLast(HTTP_SERVER_FLUSH_CONSOLIDATION, new FlushConsolidationHandler)

    if (EnableNettyLogging) {
      import io.netty.util.internal.logging.InternalLoggerFactory
      InternalLoggerFactory.setDefaultFactory(zhttp.service.logging.NettyLoggerFactory(log))
      pipeline.addLast(LOW_LEVEL_LOGGING, new LoggingHandler(LogLevel.Debug.toNettyLogLevel))
    }

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(HTTP_REQUEST_HANDLER, reqHandler)
    if (cfg.channelInitializer != null) { cfg.channelInitializer(pipeline) }
    ()
  }

}

object ServerChannelInitializer {
  private val log = Log.withTags("Server", "ChannelInitializer")
}
