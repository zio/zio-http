package zio.http

import zio.http.headers.HeaderExtension

import java.net.InetAddress

final case class Request(
  body: Body,
  headers: Headers,
  method: Method,
  url: URL,
  version: Version,
  remoteAddress: Option[InetAddress],
) extends HeaderExtension[Request] { self =>

  /**
   * Add trailing slash to the path.
   */
  def addTrailingSlash: Request = self.copy(url = self.url.addTrailingSlash)

  /**
   * Drops trailing slash from the path.
   */
  def dropTrailingSlash: Request = self.copy(url = self.url.dropTrailingSlash)

  val path = url.path

  def withMethod(method: Method): Request = self.copy(method = method)

  def withPath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  def withUrl(url: URL): Request = self.copy(url = url)

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Request = self.copy(headers = update(self.headers))
}

object Request {

  /**
   * Constructor for Request that provides sane defaults.
   */
  def make(
    version: Version = Version.`HTTP/1.1`,
    method: Method = Method.GET,
    url: URL = URL.root,
    headers: Headers = Headers.empty,
    body: Body = Body.empty,
    remoteAddress: Option[InetAddress] = None,
  ): Request =
    Request(body, headers, method, url, version, remoteAddress)
}
