package zhttp.service.server

import io.netty.channel.{Channel, ChannelHandler, ChannelPipeline}
import io.netty.handler.codec.http.HttpObjectDecoder.{
  DEFAULT_MAX_CHUNK_SIZE,
  DEFAULT_MAX_HEADER_SIZE,
  DEFAULT_MAX_INITIAL_LINE_LENGTH,
}
import io.netty.handler.codec.http.HttpServerUpgradeHandler.{UpgradeCodecFactory => JUpgradeCodecFactory}
import io.netty.handler.codec.http._
import io.netty.handler.codec.http2.{Http2CodecUtil, Http2FrameCodecBuilder, Http2ServerUpgradeCodec}
import io.netty.handler.flow.FlowControlHandler
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.util.{AsciiString => JAsciiString}
import zhttp.service.Server.Config
import zhttp.service._

case object ServerChannelInitializerUtil {

  private def upgradeCodecFactory(
    http2ReqHandler: ChannelHandler,
    http2ResHandler: ChannelHandler,
  ): JUpgradeCodecFactory = {
    new HttpServerUpgradeHandler.UpgradeCodecFactory() {
      override def newUpgradeCodec(protocol: CharSequence): Http2ServerUpgradeCodec = {
        if (JAsciiString.contentEquals(Http2CodecUtil.HTTP_UPGRADE_PROTOCOL_NAME, protocol))
          new Http2ServerUpgradeCodec(Http2FrameCodecBuilder.forServer.build, http2ReqHandler, http2ResHandler)
        else null
      }
    }
  }

  def configureClearTextHttp2(
    reqHandler: ChannelHandler,
    resHandler: ChannelHandler,
    http2ReqHandler: ChannelHandler,
    http2ResHandler: ChannelHandler,
    c: Channel,
    cfg: Config[_, Throwable],
    enableEncryptedMessageFilter: Boolean = false,
  ) = {
    val p           = c.pipeline
    val sourceCodec = new HttpServerCodec
    if (enableEncryptedMessageFilter)
      p.addLast(ENCRYPTION_FILTER_HANDLER, EncryptedMessageFilter(reqHandler, resHandler, cfg))
    p.addLast(SERVER_CODEC_HANDLER, sourceCodec)
    p.addLast(
      SERVER_CLEAR_TEXT_HTTP2_HANDLER,
      new HttpServerUpgradeHandler(sourceCodec, upgradeCodecFactory(http2ReqHandler, http2ResHandler)),
    )
    p.addLast(
      SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER,
      ClearTextHttp2FallbackServerHandler(reqHandler, resHandler, cfg),
    )
    ()
  }

  def configureClearTextHttp1(
    cfg: Config[_, Throwable],
    reqHandler: ChannelHandler,
    respHandler: ChannelHandler,
    pipeline: ChannelPipeline,
  ) = {

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
    pipeline.addLast(SERVER_OBJECT_AGGREGATOR, new HttpObjectAggregator(cfg.maxRequestSize))

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

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(HTTP_SERVER_REQUEST_HANDLER, reqHandler)

    // ServerResponseHandler - transforms Response to HttpResponse
    pipeline.addLast(HTTP_SERVER_RESPONSE_HANDLER, respHandler)

  }
}
