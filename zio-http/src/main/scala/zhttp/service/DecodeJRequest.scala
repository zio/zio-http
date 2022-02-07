package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http2.{DefaultHttp2DataFrame, Http2HeadersFrame}
import zhttp.http._

import java.io.IOException
import java.net.InetSocketAddress
import scala.annotation.tailrec

trait DecodeJRequest {

  /**
   * Tries to decode the [[io.netty.handler.codec.http2.Http2HeadersFrame]] to
   * [Request].
   */
  def decodeHttp2Header(
    hh: Http2HeadersFrame,
    ctx: ChannelHandlerContext,
    dataL: List[DefaultHttp2DataFrame] = null,
  ): Either[IOException, Request] = for {
    url <- URL.fromString(hh.headers().path().toString)
    method        = Method.fromString(hh.headers().method().toString)
    headers       = Headers.fromHttp2Headers(hh.headers())
    data          =
      if (dataL == null) HttpData.empty
      else {
        @tailrec
        def looper(byteBufList: List[ByteBuf], byteBuf: ByteBuf): ByteBuf = {
          if (byteBufList.isEmpty)
            byteBuf
          else {
            val b = byteBufList.head

            looper(byteBufList.tail, Unpooled.copiedBuffer(byteBuf, b))
          }
        }

        HttpData.fromByteBuf(looper(dataL.map(_.content()), Unpooled.EMPTY_BUFFER))
      }
    remoteAddress = {
      ctx.channel().remoteAddress() match {
        case m: InetSocketAddress => Some(m.getAddress())
        case _                    => None
      }
    }
  } yield Request(method, url, headers, remoteAddress, data)
}
