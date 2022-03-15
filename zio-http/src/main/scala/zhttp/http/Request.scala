package zhttp.http

import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import zhttp.http.headers.HeaderExtension

import java.net.InetAddress

trait Request extends HeaderExtension[Request] with HttpDataExtension[Request] { self =>

  /**
   * Updates the headers using the provided function
   */
  final override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))

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
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def version: Version                   = v
      override def unsafeEncode: HttpRequest          = self.unsafeEncode
      override def remoteAddress: Option[InetAddress] = self.remoteAddress
      override def data: HttpData                     = self.data
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
   * Gets the remote address if available
   */
  def remoteAddress: Option[InetAddress]

  /**
   * Overwrites the method in the request
   */
  def setMethod(method: Method): Request = self.copy(method = method)

  /**
   * Overwrites the path in the request
   */
  def setPath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Overwrites the url in the request
   */
  def setUrl(url: URL): Request = self.copy(url = url)

  /**
   * Returns a string representation of the request, useful for debugging,
   * logging or other purposes. It contains the essential properties of HTTP
   * request: protocol version, method, URL, headers, remote address, etc.
   * However, it does not contain a body of request, because that may not yet be
   * received at the time the method is called.
   *
   * @return
   *   a string representation of the request.
   */
  override def toString =
    s"Request($version, $method, $url, $headers, $remoteAddress)"

  /**
   * Gets the HttpRequest
   */
  private[zhttp] def unsafeEncode: HttpRequest

  /**
   * Gets the complete url
   */
  def url: URL

  /**
   * Gets the request's http protocol version
   */
  def version: Version

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
    remoteAddress: Option[InetAddress] = None,
    data: HttpData = HttpData.Empty,
  ): Request = {
    val m  = method
    val u  = url
    val h  = headers
    val ra = remoteAddress
    val d  = data
    val v  = version

    new Request {
      override def method: Method                     = m
      override def url: URL                           = u
      override def headers: Headers                   = h
      override def version: Version                   = v
      override def unsafeEncode: HttpRequest          = {
        val jVersion = v.toJava
        val path     = url.relative.encode
        new DefaultFullHttpRequest(jVersion, method.toJava, path)
      }
      override def remoteAddress: Option[InetAddress] = ra
      override def data: HttpData                     = d

    }
  }

  /**
   * Lift request to TypedRequest with option to extract params
   */
  final class ParameterizedRequest[A](req: Request, val params: A) extends Request {
    override def headers: Headers                   = req.headers
    override def method: Method                     = req.method
    override def remoteAddress: Option[InetAddress] = req.remoteAddress
    override def url: URL                           = req.url
    override def version: Version                   = req.version
    override def unsafeEncode: HttpRequest          = req.unsafeEncode
    override def data: HttpData                     = req.data
    override def toString: String                   =
      s"ParameterizedRequest($req, $params)"
  }

  object ParameterizedRequest {
    def apply[A](req: Request, params: A): ParameterizedRequest[A] = new ParameterizedRequest(req, params)
  }
}
