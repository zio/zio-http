package zhttp.service.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{Channel, ChannelHandler, ChannelInitializer}
import io.netty.handler.codec.http.HttpObjectDecoder.{DEFAULT_MAX_CHUNK_SIZE, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_INITIAL_LINE_LENGTH}
import io.netty.handler.codec.http.HttpServerUpgradeHandler.UpgradeCodecFactory
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2.{Http2CodecUtil, Http2FrameCodecBuilder, Http2ServerUpgradeCodec}
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.util.AsciiString
import zhttp.service.Server.Config
import zhttp.service._

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
final case class ServerChannelInitializer[R](
  zExec: HttpRuntime[R],
  cfg: Config[R, Throwable],
  reqHandler: ChannelHandler,
  respHandler: ChannelHandler,
) extends ChannelInitializer[Channel] {
  override def initChannel(channel: Channel): Unit = {
    if (!cfg.http2) {
      // !! IMPORTANT !!
      // Order of handlers are critical to make this work
      val pipeline = channel.pipeline()
      // SSL
      // Add SSL Handler if CTX is available
      cfg.sslOption match {
        case Some(sslOption) =>
          pipeline.addFirst(
            SSL_HANDLER,
            new OptionalSSLHandler(sslOption.build(cfg.http2), sslOption.httpBehaviour, cfg),
          )
        case None            => ()
      }

      // ServerCodec
      // Instead of ServerCodec, we should use Decoder and Encoder separately to have more granular control over performance.
      pipeline.addLast(
        SERVER_DECODER_HANDLER,
        new HttpRequestDecoder(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE, false),
      )
      pipeline.addLast(SERVER_ENCODER_HANDLER, new HttpResponseEncoder())

      // TODO: See if server codec is really required

      // ObjectAggregator
      // Always add ObjectAggregator
      pipeline.addLast(SERVER_OBJECT_AGGREGATOR_HANDLER, new HttpObjectAggregator(cfg.maxRequestSize))

      // ExpectContinueHandler
      // Add expect continue handler is settings is true
      if (cfg.acceptContinue)
        pipeline.addLast(HTTP_SERVER_EXPECT_CONTINUE_HANDLER, new HttpServerExpectContinueHandler())

      // KeepAliveHandler
      // Add Keep-Alive handler is settings is true
      if (cfg.keepAlive) pipeline.addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)

      // FlowControlHandler
      // Required because HttpObjectDecoder fires an HttpRequest that is immediately followed by a LastHttpContent event.
      // For reference: https://netty.io/4.1/api/io/netty/handler/flow/FlowControlHandler.html
      if (cfg.flowControl) pipeline.addLast(FLOW_CONTROL_HANDLER, new FlowControlHandler())

      // FlushConsolidationHandler
      // Flushing content is done in batches. Can potentially improve performance.
      if (cfg.consolidateFlush) pipeline.addLast(HTTP_SERVER_FLUSH_CONSOLIDATION_HANDLER, new FlushConsolidationHandler)

      // RequestHandler
      // Always add ZIO Http Request Handler
      pipeline.addLast(HTTP_SERVER_REQUEST_HANDLER, reqHandler)

      // ServerResponseHandler - transforms Response to HttpResponse
      pipeline.addLast(HTTP_SERVER_RESPONSE_HANDLER, respHandler)

      ()
    } else if (cfg.http2) {
      val pipeline = channel.pipeline()

      cfg.sslOption match {
        case Some(sslOption) =>
          pipeline.addFirst(
            SSL_HANDLER,
            new OptionalSSLHandler(sslOption.build(cfg.http2), sslOption.httpBehaviour, cfg),
          )
          pipeline
            .addLast(HTTP2_SERVER_CODEC_HANDLER, Http2FrameCodecBuilder.forServer().build())
//            .addLast(HTTP2_REQUEST_HANDLER, ???)
          ()
        case None            =>
          val sourceCodec = new HttpServerCodec
//          pipeline.addLast(ENCRYPTION_FILTER_HANDLER, EncryptedMessageFilter(httpH, settings))
          pipeline.addLast(SERVER_CODEC_HANDLER, sourceCodec)
          pipeline.addLast(SERVER_CLEAR_TEXT_HTTP2_HANDLER, new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2H)))
//          pipeline.addLast(SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER, ClearTextHttp2FallbackServerHandler(httpH, settings))
          ()
      }

    }
  }

  private def upgradeCodecFactory(http2H: ChannelHandler): UpgradeCodecFactory = {
    new HttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): Http2ServerUpgradeCodec = {
        if (AsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer.build, http2H)
        else null
      }
    }
  }

}
