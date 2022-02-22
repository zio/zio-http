package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.compression.CompressionOptions
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCountUtil
import zio.Chunk

import java.util.{List => JList}

class AggregatesResponseCompressionHandler(compressionOptions: Chunk[CompressionOptions])
    extends HttpContentCompressor(compressionOptions: _*) {
  import AggregatesResponseCompressionHandler._

  override protected[zhttp] def encode(ctx: ChannelHandlerContext, msg: HttpObject, out: JList[AnyRef]): Unit = {
    val fullHttpResponse = msg.asInstanceOf[FullHttpResponse]
    val acceptEncoding   = fullHttpResponse.headers().get(HttpHeaderNames.ACCEPT_ENCODING)

    if (isPassthru(fullHttpResponse.protocolVersion(), fullHttpResponse.status().code())) { unsafeAdd(msg, out): Unit }
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
  }
}

object AggregatesResponseCompressionHandler {
  private def isPassthru(version: HttpVersion, code: Int) =
    code < 200 || code == 204 || code == 304 || (version == HttpVersion.HTTP_1_0)

  private def unsafeAdd(msg: HttpObject, out: JList[AnyRef]) = out.add(ReferenceCountUtil.retain(msg))
}
