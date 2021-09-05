package zhttp.service.server

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandler, ChannelHandlerContext}
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.http.{HttpObjectAggregator, HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler}
import io.netty.handler.ssl.SslHandler
import zhttp.service.Server.Settings
import zhttp.service._

import java.util

case class EncryptedMessageFilter[R](httpH: ChannelHandler, settings: Settings[R, Throwable])
    extends ByteToMessageDecoder {
  override def decode(context: ChannelHandlerContext, in: ByteBuf, out: util.List[AnyRef]): Unit = {
    if (SslHandler.isEncrypted(in)) {
      context.channel().pipeline().remove(SERVER_CLEAR_TEXT_HTTP2_HANDLER)
      context.channel().pipeline().remove(SERVER_CLEAR_TEXT_HTTP2_FALLBACK_HANDLER)
      context
        .channel()
        .pipeline()
        .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
        .addLast(OBJECT_AGGREGATOR, new HttpObjectAggregator(settings.maxRequestSize))
        .addLast(HTTP_REQUEST_HANDLER, httpH)
        .remove(this)
      ()
    } else {
      context.pipeline().remove(this)
      ()
    }
  }
}
