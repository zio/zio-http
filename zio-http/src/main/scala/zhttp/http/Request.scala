package zhttp.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpHeaderNames

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

  def getBodyAsString: Option[String] = content match {
    case HttpData.CompleteData(data) => Option(data.map(_.toChar).mkString)
    case _                           => Option.empty
  }

  def remoteAddress: Option[InetAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    else
      None
  }

}

object Request {
  def cookies(cookies: List[Cookie]): Header =
    Header(
      HttpHeaderNames.COOKIE,
      cookies.map(p => p.name + "=" + p.content) mkString "; ",
    )

  def cookiesFromHeader(headers: List[Header]): Header = Header(
    HttpHeaderNames.COOKIE,
    Response.cookies(headers).map(p => p.name + "=" + p.content) mkString "; ",
  )
}
