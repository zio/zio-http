package zhttp.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpRequest}
import zhttp.http.headers.HeaderExtension
import zhttp.service.{Ctx, Handler}

import java.io.IOException

trait Request extends HeaderExtension[Request] { self =>

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

  /**
   * Add trailing slash to the path.
   */
  final def addTrailingSlash: Request = self.copy(url = self.url.addTrailingSlash)

  /**
   * Decodes the body as a Body
   */
  def body: Body

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
      override def body: Body                           = self.body
      override def unsafeContext: ChannelHandlerContext = self.unsafeContext
    }
  }

  /**
   * Remove trailing slash from path.
   */
  final def dropTrailingSlash: Request = self.copy(url = self.url.dropTrailingSlash)

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
   * Gets the complete url
   */
  def url: URL

  /**
   * Gets the request's http protocol version
   */
  def version: Version

  /**
   * Accesses the channel's context for more low level control
   */
  private[zhttp] def unsafeContext: ChannelHandlerContext

  /**
   * Gets the HttpRequest
   */
  private[zhttp] def unsafeEncode: HttpRequest

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
    body: Body = Body.empty,
  ): Request = {
    val m = method
    val u = url
    val h = headers
    val d = body
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
      override def body: Body                           = d
      override def unsafeContext: ChannelHandlerContext = throw new IOException("Request does not have a context")

    }
  }

  private[zhttp] def fromFullHttpRequest(jReq: FullHttpRequest)(implicit ctx: Ctx): Request = {

    new Request {
      override def method: Method            = Method.fromHttpMethod(jReq.method())
      override def url: URL                  = URL.fromString(jReq.uri()).getOrElse(URL.empty)
      override def headers: Headers          = Headers.make(jReq.headers())
      override def body: Body                = Body.fromByteBuf(jReq.content())
      override def version: Version          = Version.unsafeFromJava(jReq.protocolVersion())
      override def unsafeEncode: HttpRequest = jReq
      override def unsafeContext: Ctx        = ctx
    }
  }

  private[zhttp] def fromHttpRequest(jReq: HttpRequest)(implicit ctx: Ctx): Request = {
    new Request {
      override def headers: Headers          = Headers.make(jReq.headers())
      override def method: Method            = Method.fromHttpMethod(jReq.method())
      override def url: URL                  = URL.fromString(jReq.uri()).getOrElse(URL.empty)
      override def version: Version          = Version.unsafeFromJava(jReq.protocolVersion())
      override def unsafeEncode: HttpRequest = jReq
      override def unsafeContext: Ctx        = ctx
      override def body: Body                = Body.fromAsync { async => Handler.Unsafe.addContentHandler(async) }
    }
  }
}
