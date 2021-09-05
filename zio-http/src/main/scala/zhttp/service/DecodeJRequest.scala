package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http2.{DefaultHttp2DataFrame, Http2HeadersFrame}
import zhttp.http._

import scala.annotation.tailrec

trait DecodeJRequest {

  /**
   * Tries to decode the [[io.netty.handler.codec.http.FullHttpRequest]] to [Request].
   */
  def decodeJRequest(jReq: FullHttpRequest, ctx: ChannelHandlerContext): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = HttpData.fromByteBuf(jReq.content())
  } yield Request(endpoint, headers, data, ctx)

 /**
  * Tries to decode the [[io.netty.handler.codec.http2.Http2HeadersFrame]] to [Request].
  */
  def decodeHttp2Header(
                         hh: Http2HeadersFrame,
                         ctx: ChannelHandlerContext,
                         dataL: List[DefaultHttp2DataFrame] = null,
                       ): Either[HttpError, Request]  = for {
    url <- URL.fromString(hh.headers().path().toString)
    method   = Method.fromString(hh.headers().method().toString)
    headers  = Header.fromHttp2Headers(hh.headers())
    endpoint = method -> url
    data     =
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
  } yield Request(endpoint, headers, data, ctx)

}
