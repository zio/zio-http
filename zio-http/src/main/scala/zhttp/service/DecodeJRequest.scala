package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.handler.codec.http2.{
  DefaultHttp2DataFrame => JDefaultHttp2DataFrame,
  Http2HeadersFrame => JHttp2HeadersFrame,
}
import zhttp.core.{JChannelHandlerContext, JFullHttpRequest}
import zhttp.http._

import scala.annotation.tailrec

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest, ctx: JChannelHandlerContext): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromJHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = HttpData.fromByteBuf(jReq.content())
  } yield Request(endpoint, headers, data, ctx)
  def decodeHttp2Header(
    hh: JHttp2HeadersFrame,
    ctx: JChannelHandlerContext,
    dataL: List[JDefaultHttp2DataFrame] = null,
  ): Either[HttpError, Request]                                                                       = for {
    url <- URL.fromString(hh.headers().path().toString)
    method   = Method.fromString(hh.headers().method().toString)
    headers  = Header.make(hh.headers())
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
