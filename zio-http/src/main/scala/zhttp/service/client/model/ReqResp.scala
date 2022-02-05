package zhttp.service.client.model

import io.netty.buffer.{ByteBuf, ByteBufAllocator, ByteBufUtil}
import io.netty.channel.ChannelHandlerContext
import zhttp.http._
import zhttp.http.headers.HeaderExtension
import zio.{Chunk, Task}

import java.net.{InetAddress, InetSocketAddress}

object Resp                       {
  def empty = Resp(zhttp.http.Status.NOT_FOUND, Headers.empty, ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, ""))
}
final case class Resp(status: zhttp.http.Status, headers: Headers, private val buffer: ByteBuf)
    extends HeaderExtension[Resp] { self =>

  def getBodyAsString: Task[String] = Task(buffer.toString(self.getCharset))

  def getBody: Task[Chunk[Byte]] = Task(Chunk.fromArray(ByteBufUtil.getBytes(buffer)))

  override def getHeaders: Headers = headers

  override def updateHeaders(update: Headers => Headers): Resp =
    self.copy(headers = update(headers))

}

final case class ReqParams(
  method: Method = Method.GET,
  url: URL,
  getHeaders: Headers = Headers.empty,
  data: HttpData = HttpData.empty,
  private val channelContext: ChannelHandlerContext = null,
) extends HeaderExtension[ReqParams] { self =>

  def getBodyAsString: Option[String] = data match {
    case HttpData.Text(text, _)       => Some(text)
    case HttpData.BinaryChunk(data)   => Some(new String(data.toArray, HTTP_CHARSET))
    case HttpData.BinaryByteBuf(data) => Some(data.toString(HTTP_CHARSET))
    case _                            => Option.empty
  }

  def remoteAddress: Option[InetAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    else
      None
  }

  def remoteAddressAndPort: Option[InetSocketAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress])
    else
      None
  }

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): ReqParams =
    self.copy(getHeaders = update(self.getHeaders))
}
