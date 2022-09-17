package zio.http

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpRequest}
import zio.Unsafe
import zio.http.headers.HeaderExtension
import zio.http.netty._
import zio.http.service.Ctx

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
      override final val method: Method   = m
      override final val url: URL         = u
      override final val headers: Headers = h
      override final val version: Version = v
      override final val body: Body       = self.body

      override final val unsafe: UnsafeAPI = new UnsafeAPI {
        override final def context(implicit unsafe: Unsafe): ChannelHandlerContext = self.unsafe.context
        override final def encode(implicit unsafe: Unsafe): HttpRequest            = self.unsafe.encode
      }
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

  private[zio] trait UnsafeAPI {

    /**
     * Accesses the channel's context for more low level control
     */
    def context(implicit unsafe: Unsafe): ChannelHandlerContext

    /**
     * Gets the HttpRequest
     */
    def encode(implicit unsafe: Unsafe): HttpRequest
  }

  private[zio] val unsafe: UnsafeAPI
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
      override final val method: Method   = m
      override final val url: URL         = u
      override final val headers: Headers = h
      override final val version: Version = v
      override final val body: Body       = d

      override final val unsafe: UnsafeAPI = new UnsafeAPI {
        override final def context(implicit unsafe: Unsafe): ChannelHandlerContext = throw new IOException(
          "Request does not have a context",
        )
        override final def encode(implicit unsafe: Unsafe): HttpRequest            = {
          val jVersion = v.toJava
          val path     = url.relative.encode
          new DefaultFullHttpRequest(jVersion, method.toJava, path)
        }
      }
    }
  }

  private[zio] def fromFullHttpRequest(jReq: FullHttpRequest)(implicit ctx: Ctx): Request = {
    val protocolVersion = Version.unsafe.fromJava(jReq.protocolVersion())(Unsafe.unsafe)

    new Request {
      override final def method: Method    = Method.fromHttpMethod(jReq.method())
      override final def url: URL          = URL.fromString(jReq.uri()).getOrElse(URL.empty)
      override final def headers: Headers  = Headers.make(jReq.headers())
      override final def body: Body        = Body.fromByteBuf(jReq.content())
      override final val version: Version  = protocolVersion
      override final val unsafe: UnsafeAPI = new UnsafeAPI {
        override final def encode(implicit unsafe: Unsafe): HttpRequest = jReq
        override final def context(implicit unsafe: Unsafe): Ctx        = ctx
      }
    }
  }

  private[zio] def fromHttpRequest(jReq: HttpRequest)(implicit ctx: Ctx): Request = {
    val protocolVersion = Version.unsafe.fromJava(jReq.protocolVersion())(Unsafe.unsafe)

    new Request {
      override final def headers: Headers = Headers.make(jReq.headers())
      override final def method: Method   = Method.fromHttpMethod(jReq.method())
      override final def url: URL         = URL.fromString(jReq.uri()).getOrElse(URL.empty)
      override final val version: Version = protocolVersion
      override final def body: Body       = Body.fromAsync { async =>
        ctx.addAsyncBodyHandler(async)(Unsafe.unsafe)
      }

      override final val unsafe: UnsafeAPI = new UnsafeAPI {
        override final def encode(implicit unsafe: Unsafe): HttpRequest = jReq

        override final def context(implicit unsafe: Unsafe): Ctx = ctx
      }
    }
  }
}
