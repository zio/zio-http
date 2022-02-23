package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.compression.CompressionOptions
import io.netty.handler.codec.http._
import zhttp.service.server.content.compression.Compression
import zio.Chunk

import java.util.{List => JList}

class AggregatesResponseCompressionHandler(compressionOptions: Chunk[CompressionOptions])
    extends HttpContentCompressor(compressionOptions: _*)
    with Compression {

  override protected[zhttp] def encode(ctx: ChannelHandlerContext, msg: HttpObject, out: JList[AnyRef]): Unit = {
    val fullHttpResponse = msg.asInstanceOf[FullHttpResponse]
    val acceptEncoding   = fullHttpResponse.headers().get(HttpHeaderNames.ACCEPT_ENCODING)

    if (acceptEncoding == null) unsafeAdd(msg, out): Unit
    else if (isPassthru(fullHttpResponse.protocolVersion(), fullHttpResponse.status().code())) unsafeAdd(msg, out): Unit
    else {

      val result              = beginEncode(fullHttpResponse, acceptEncoding)
      lazy val targetEncoding = result.targetContentEncoding()
      val encoder             = result.contentEncoder()

      if (result == null) unsafeAdd(msg, out): Unit

      fullHttpResponse
        .headers()
        .set(HttpHeaderNames.CONTENT_ENCODING, targetEncoding)
        .remove(HttpHeaderNames.ACCEPT_ENCODING)

      val content = fullHttpResponse.content
      encoder.writeOutbound(content.retain())

      val res = encoder.readOutbound[ByteBuf]()

      HttpUtil.setContentLength(fullHttpResponse, res.readableBytes().toLong)
      unsafeAdd(fullHttpResponse.replace(res), out): Unit
    }

    ctx.pipeline().remove(ctx.name()): Unit
  }
}
