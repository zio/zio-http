package zhttp.service.client

import example.client.Http2SettingsHandler
import io.netty.channel.{Channel, ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.http.{FullHttpRequest, HttpClientCodec, HttpObjectAggregator}
import io.netty.handler.codec.http2.HttpToHttp2ConnectionHandler
import io.netty.handler.ssl.{ApplicationProtocolNames, ApplicationProtocolNegotiationHandler}
import zhttp.service._

final case class Http2OrHttpClientHandler(
  ch: Channel,
  connectionHandler: HttpToHttp2ConnectionHandler,
  settingsHandler: Http2SettingsHandler,
  httpResponseHandler: ChannelHandler,
  http2ResponseHandler: Http2ClientResponseHandler,
  jReq: FullHttpRequest,
) extends ApplicationProtocolNegotiationHandler(ApplicationProtocolNames.HTTP_1_1) {
  override protected def configurePipeline(ctx: ChannelHandlerContext, protocol: String): Unit = {

    val pipeline = ch.pipeline
    if (ApplicationProtocolNames.HTTP_2 == protocol) {
      pipeline.addLast(HTTP2_CONNECTION_HANDLER, connectionHandler)
      pipeline.addLast(HTTP2_SETTINGS_HANDLER, settingsHandler)
      pipeline.addLast(HTTP2_RESPONSE_HANDLER, http2ResponseHandler)
      ()
    } else if (ApplicationProtocolNames.HTTP_1_1 == protocol) {
      pipeline.addLast(CLIENT_CODEC_HANDLER, new HttpClientCodec)
      pipeline.addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
      pipeline.addLast(HTTP_RESPONSE_HANDLER, httpResponseHandler)
      ctx.channel().writeAndFlush(jReq)
      ch.pipeline().remove(this)
      ()
    } else {
      throw new IllegalStateException("unknown protocol: " + protocol)
    }
  }
}
