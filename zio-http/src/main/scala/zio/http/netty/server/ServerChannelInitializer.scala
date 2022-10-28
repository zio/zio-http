package zio.http.netty.server

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
import zio._
import zio.http.ServerConfig
import zio.http.netty.Names
import zio.http.netty.server.ServerChannelInitializer.log
import zio.http.service.Log
import zio.http.service.logging.LogLevelTransform._
import zio.logging.LogLevel
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
private[zio] final case class ServerChannelInitializer(
  cfg: ServerConfig,
  reqHandler: ChannelInboundHandler,
  enableNettyLogging: Boolean = false,
) extends ChannelInitializer[Channel] {

  override def initChannel(channel: Channel): Unit = {
    // !! IMPORTANT !!
    // Order of handlers are critical to make this work
    val pipeline = channel.pipeline()
    log.debug(s"Connection initialized: ${channel.remoteAddress()}")
    // SSL
    // Add SSL Handler if CTX is available
    cfg.sslConfig.foreach { sslCfg =>
      pipeline.addFirst(Names.SSLHandler, new ServerSSLDecoder(sslCfg, cfg))
    }

    // ServerCodec
    // Instead of ServerCodec, we should use Decoder and Encoder separately to have more granular control over performance.
    pipeline.addLast(
      "decoder",
      new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, cfg.maxHeaderSize, DEFAULT_MAX_CHUNK_SIZE, false),
    )
    pipeline.addLast("encoder", new HttpResponseEncoder())

    // HttpContentDecompressor
    if (cfg.requestDecompression.enabled)
      pipeline.addLast(Names.HttpRequestDecompression, new HttpContentDecompressor(cfg.requestDecompression.strict))

    cfg.responseCompression.foreach(ops => {
      pipeline.addLast(
        Names.HttpResponseCompression,
        new HttpContentCompressor(ops.contentThreshold, ops.options.map(_.toJava): _*),
      )
    })

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

object ServerChannelInitializer {
  implicit val trace: Trace = Trace.empty

  private val log = Log.withTags("Server", "Channel")

  val layer = ZLayer.fromZIO {
    for {
      cfg     <- ZIO.service[ServerConfig]
      handler <- ZIO.service[SimpleChannelInboundHandler[HttpObject]]
    } yield ServerChannelInitializer(cfg, handler, false) // TODO add Netty logging flag to ServerConfig.
  }
}
