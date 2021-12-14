package zhttp.service.client.content.handlers

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{FullHttpResponse, HttpHeaderNames}
import zhttp.http.{HTTP_CHARSET, Header, Status}
import zhttp.service.Client
import zhttp.service.Client.{ClientResponse, StringContent}

final case class DynamicContentHandler() extends SimpleChannelInboundHandler[FullHttpResponse](true) {

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
    val status            = Status.fromHttpResponseStatus(msg.status())
    val headers           = Header.parse(msg.headers())
    val content           = Unpooled.copiedBuffer(msg.content())
    val contentTypeHeader = headers.find(_.name == HttpHeaderNames.CONTENT_TYPE)

    val response: ClientResponse = contentTypeHeader match {
      case Some(value) =>
        value match {
          case Header.contentTypeTextPlain =>
            ContentHandler.getStringContentResponse(status, headers, content)
          case Header.contentTypeJson      =>
            ContentHandler.getStringContentResponse(status, headers, content)
          case _                           => ContentHandler.getStringContentResponse(status, headers, content)
        }
      case None        => ContentHandler.getStringContentResponse(status, headers, content)
    }

    ctx.fireChannelRead(response)
    ()
  }
}

object ContentHandler {

  def getStringContentResponse(status: Status, headers: List[Header], content: ByteBuf): ClientResponse =
    Client.ClientResponse(status, headers, StringContent(content.toString(HTTP_CHARSET)))
}
