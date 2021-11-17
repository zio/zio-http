package zhttp.service.client

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.{HttpClientCodec, HttpObjectAggregator}
import io.netty.handler.ssl.SslHandler
import zhttp.service._

import java.util

class OptionalClientSSLHandler(httpResponseHandler: ChannelHandler) extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (in.readableBytes < 5)
      ()
    else if (SslHandler.isEncrypted(in)) {
      context.pipeline().remove(this)
      ()
    } else {
      context.pipeline().remove(HTTP2_OR_HTTP_CLIENT_HANDLER)
      context
        .pipeline()
        .addLast(CLIENT_CODEC_HANDLER, new HttpClientCodec)
        .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue))
        .addLast(HTTP_RESPONSE_HANDLER, httpResponseHandler)
      context.pipeline().remove(this)
      ()
    }
  }
}
