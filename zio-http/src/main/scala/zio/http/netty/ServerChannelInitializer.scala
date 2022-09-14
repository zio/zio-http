package zio.http
package netty

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.HttpObjectDecoder.{
  DEFAULT_MAX_CHUNK_SIZE,
  DEFAULT_MAX_HEADER_SIZE,
  DEFAULT_MAX_INITIAL_LINE_LENGTH,
}
import io.netty.handler.codec.http._
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.logging.LoggingHandler
import ServerChannelInitializer.log
import zio.http.service.logging.LogLevelTransform._
import zio.logging.LogLevel
// import zio._


trait ZIOServerChannelInitializer extends ChannelInitializer[Channel]

object ZIOServerChannelInitializer {

  // def layer[R] = for {
  //  driver <- ZIO.service[NettyDriver[R]]
  // } yield ()
}


/**
 * Initializes the netty channel with default handlers
 */
@Sharable
private[zio] final case class ServerChannelInitializer[R](
  cfg: ServerConfig,
  reqHandler: ChannelHandler,
  enableNettyLogging: Boolean = false
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
        .addFirst(Names.SSLHandler, new ServerSSLDecoder(sslctx, cfg.sslOption.httpBehaviour, cfg))

    // ServerCodec
    // Instead of ServerCodec, we should use Decoder and Encoder separately to have more granular control over performance.
    pipeline.addLast(
      "decoder",
      new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE, false),
    )
    pipeline.addLast("encoder", new HttpResponseEncoder())

    // HttpContentDecompressor
    if (cfg.requestDecompression._1)
      pipeline.addLast(Names.HttpRequestDecompression, new HttpContentDecompressor(cfg.requestDecompression._2))

    // TODO: See if server codec is really required

    // ObjectAggregator
    // Always add ObjectAggregator
    if (cfg.useAggregator)
      pipeline.addLast(Names.HttpObjectAggregator, new HttpObjectAggregator(cfg.objectAggregator))

    // ExpectContinueHandler
    // Add expect continue handler is settings is true
    if (cfg.acceptContinue) pipeline.addLast(Names.HttpServerExpectContinue, new HttpServerExpectContinueHandler())

    // KeepAliveHandler
    // Add Keep-Alive handler is settings is true
    if (cfg.keepAlive) pipeline.addLast(Names.HttpKeepAliveHandler, new HttpServerKeepAliveHandler)

    // FlowControlHandler
    // Required because HttpObjectDecoder fires an HttpRequest that is immediately followed by a LastHttpContent event.
    // For reference: https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html
    if (cfg.flowControl) pipeline.addLast(Names.FlowControlHandler, new FlowControlHandler())

    // FlushConsolidationHandler
    // Flushing content is done in batches. Can potentially improve performance.
    if (cfg.consolidateFlush) pipeline.addLast(Names.HttpServerFlushConsolidation, new FlushConsolidationHandler)

    if (enableNettyLogging) {
      import io.netty.util.internal.logging.InternalLoggerFactory
      InternalLoggerFactory.setDefaultFactory(zio.http.service.logging.NettyLoggerFactory(log))
      pipeline.addLast(Names.LowLevelLogging, new LoggingHandler(LogLevel.Debug.toNettyLogLevel))
    }

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(Names.HttpRequestHandler, reqHandler)
    // TODO: find a different approach if (cfg.channelInitializer != null) { cfg.channelInitializer(pipeline) }
    ()
  }

}

private[zio] object ServerChannelInitializer {
  private val log = service.Log.withTags("Server", "Channel")
}
