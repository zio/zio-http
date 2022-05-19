package zhttp.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import zhttp.http.headers.HeaderExtension

import java.io.IOException

trait Request extends HeaderExtension[Request] with HttpDataExtension[Request] { self =>

  /**
   * Accesses the channel's context for more low level control
   */
  private[zhttp] def unsafeContext: ChannelHandlerContext

  /**
   * Gets the HttpRequest
   */
  private[zhttp] def unsafeEncode: HttpRequest

  def copy(
    version: Version = self.version,
    method: Method = self.method,
    url: URL = self.url,
    headers: Headers = self.headers,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val v = version
    new Request {
      override def method: Method                       = m
      override def url: URL                             = u
      override def headers: Headers                     = h
      override def version: Version                     = v
      override def unsafeEncode: HttpRequest            = self.unsafeEncode
      override def data: HttpData                       = self.data
      override def unsafeContext: ChannelHandlerContext = self.unsafeContext
    }
  }

  /**
   * Decodes the body as a HttpData
   */
  def data: HttpData

  /**
   * Gets all the headers in the Request
   */
  def headers: Headers

  /**
   * Checks is the request is a pre-flight request or not
   */
  def isPreflight: Boolean = method == Method.OPTIONS

  /**
   * Gets the request's method
   */
  def method: Method

  /**
   * Gets the request's path
   */
  def path: Path = url.path

  /**
   * Gets the complete url
   */
  def url: URL

  /**
   * Gets the request's http protocol version
   */
  def version: Version

  /**
   * Overwrites the method in the request
   */
  final def setMethod(method: Method): Request = self.copy(method = method)

  /**
   * Overwrites the path in the request
   */
  final def setPath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Overwrites the url in the request
   */
  final def setUrl(url: URL): Request = self.copy(url = url)

  /**
   * Returns a string representation of the request, useful for debugging,
   * logging or other purposes. It contains the essential properties of HTTP
   * request: protocol version, method, URL, headers and remote address.
   */
  final override def toString = s"Request($version, $method, $url, $headers)"

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

}

object Request {

  /**
   * Constructor for Request
   */
  def apply(
    version: Version = Version.`HTTP/1.1`,
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    data: HttpData = HttpData.Empty,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val d = data
    val v = version

    new Request {
      override def method: Method                       = m
      override def url: URL                             = u
      override def headers: Headers                     = h
      override def version: Version                     = v
      override def unsafeEncode: HttpRequest            = {
        val jVersion = v.toJava
        val path     = url.relative.encode
        new DefaultFullHttpRequest(jVersion, method.toJava, path)
      }
      override def data: HttpData                       = d
      override def unsafeContext: ChannelHandlerContext = throw new IOException("Request does not have a context")

    }
  }
}
