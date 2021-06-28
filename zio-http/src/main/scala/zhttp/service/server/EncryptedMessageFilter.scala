package zhttp.service.server

import io.netty.handler.codec.http.{HttpServerKeepAliveHandler => JHttpServerKeepAliveHandler}
import io.netty.handler.codec.{ByteToMessageDecoder => JByteToMessageDecoder}
import io.netty.handler.ssl.{SslHandler => JSslHandler}
import zhttp.core.{JByteBuf, JChannelHandler, JChannelHandlerContext, JHttpObjectAggregator}
import zhttp.service.Server.Settings
import zhttp.service._

import java.util

case class EncryptedMessageFilter[R](httpH: JChannelHandler, settings: Settings[R, Throwable])
    extends JByteToMessageDecoder {
  override def decode(context: JChannelHandlerContext, in: JByteBuf, out: util.List[AnyRef]): Unit = {
    if (JSslHandler.isEncrypted(in)) {
      context.channel().pipeline().remove(CLEAR_TEXT_HTTP2_HANDLER)
      context.channel().pipeline().remove(CLEAR_TEXT_HTTP2_FALLBACK_HANDLER)
      context
        .channel()
        .pipeline()
        .addLast(HTTP_KEEPALIVE_HANDLER, new JHttpServerKeepAliveHandler)
        .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(settings.maxRequestSize))
        .addLast(HTTP_REQUEST_HANDLER, httpH)
        .remove(this)
      ()
    } else {
      context.pipeline().remove(this)
      ()
    }
  }
}
