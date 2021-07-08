package zhttp.http

import io.netty.channel.ChannelHandlerContext

import java.net.{InetAddress, InetSocketAddress}

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

  val charSet                         = getCharSet
  def getBodyAsString: Option[String] = content match {
    case HttpData.CompleteData(data) =>
      charSet match {
        case Some(value) => Option(new String(data.toArray, value))
        case None        => Option(data.map(_.toChar).mkString)
      }
    case _                           => Option.empty
  }

  def remoteAddress: Option[InetAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    else
      None
  }

}

object Request {}
