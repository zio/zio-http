package zhttp.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpHeaderNames, HttpUtil}

import java.net.{InetAddress, InetSocketAddress}
import java.nio.charset.Charset

// REQUEST
final case class Request(
  endpoint: Endpoint,
  headers: List[Header] = List.empty,
  content: HttpData[Any, Nothing] = HttpData.empty,
  private val channelContext: ChannelHandlerContext = null,
) extends HasHeaders
    with HeadersHelpers { self =>
  val method: Method = endpoint._1
  val url: URL       = endpoint._2
  val route: Route   = method -> url.path

  val getCharset: Option[Charset] =
    getHeaderValue(HttpHeaderNames.CONTENT_TYPE).map(HttpUtil.getCharset(_, HTTP_CHARSET))

  def getBodyAsString: Option[String] = content match {
    case HttpData.Text(text, _)       => Option(text)
    case HttpData.BinaryChunk(data)   => Option(new String(data.toArray, getCharset.getOrElse(HTTP_CHARSET)))
    case HttpData.BinaryByteBuf(data) => Option(data.toString(getCharset.getOrElse(HTTP_CHARSET)))
    case HttpData.BinaryStream(_)     => Option.empty
    case HttpData.Empty               => Option.empty
  }

  def remoteAddress: Option[InetAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    else
      None
  }

}

object Request {}
