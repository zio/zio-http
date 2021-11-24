package zhttp.http

import io.netty.channel.ChannelHandlerContext

import java.net.{InetAddress, InetSocketAddress}

final case class Request(method: Method= Method.GET, url: URL= URL.root, headers: List[Header]= List.empty, data: HttpData[Any, Nothing] = HttpData.empty, private val channelContext: ChannelHandlerContext = null) extends HeaderExtension[Request] { self =>

  /**
   * Decodes the content of the request using the provided ContentDecoder
   */
  def getBody: HttpData[Any,Nothing] = data

  /**
   * Decodes the content of request as string
   */
  def getBodyAsString: Option[String] = data match {
    case HttpData.Text(text, _) => Option(text)
    case HttpData.BinaryChunk(data) => Option(new String(data.toArray, getCharset.getOrElse(HTTP_CHARSET)))
    case HttpData.BinaryByteBuf(data) => Option(data.toString(getCharset.getOrElse(HTTP_CHARSET)))
    case HttpData.BinaryStream(_) =>None
    case HttpData.Empty => None
  }

  /**
   * Gets all the headers in the Request
   */
  def getHeaders: List[Header] = headers

  /**
   * Checks is the request is a pre-flight request or not
   */
  def isPreflight: Boolean = method == Method.OPTIONS

  /**
   * Gets the request's path
   */
  def path: Path = url.path


  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(f: List[Header] => List[Header]): Request = self.copy(headers = f(self.getHeaders))

  /**
   * Gets the remote address if available
   */
  def remoteAddress: Option[InetAddress] = {
    if (channelContext != null && channelContext.channel().remoteAddress().isInstanceOf[InetSocketAddress])
      Some(channelContext.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
    else
      None
  }
}

object Request {


  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) {
     def getBody[R, B]: HttpData[Any,Nothing] = req.getBody

     def getHeaders: List[Header] = req.getHeaders

     def method: Method = req.method

     def remoteAddress: Option[InetAddress] = req.remoteAddress

     def url: URL = req.url
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }

}
