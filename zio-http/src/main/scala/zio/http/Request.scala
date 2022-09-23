package zio.http

import zio.Trace
import zio.http.model._
import zio.http.model.headers.HeaderExtension

import java.net.InetAddress

trait Request extends HeaderExtension[Request] {
  def body: Body
  def headers: Headers
  def method: Method
  def path: Path
  def url: URL
  def version: Version
  def remoteAddress: Option[InetAddress]
  def updateMethod(newMethod: Method): Request
  def updateUrl(newUrl: URL): Request
  def updateVersion(newVersion: Version): Request

  /**
   * Drops trailing slash from the path.
   */
  final def dropTrailingSlash: Request = updateUrl(url.dropTrailingSlash)

  final def updatePath(newPath: Path): Request = updateUrl(url.copy(path = newPath))

}

object Request {

  final case class ClientRequest(
    body: Body,
    headers: Headers,
    method: Method,
    url: URL,
    version: Version,
    remoteAddress: Option[InetAddress],
  ) extends Request { self =>

    /**
     * Add trailing slash to the path.
     */
    def addTrailingSlash: ClientRequest = self.copy(url = self.url.addTrailingSlash)

    val path = url.path

    /**
     * Updates the headers using the provided function
     */
    override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Request =
      self.copy(headers = update(self.headers))

    override def updateMethod(newMethod: Method): Request = self.copy(method = newMethod)

    override def updateUrl(newUrl: URL): Request = self.copy(url = newUrl)

    override def updateVersion(newVersion: Version): Request = self.copy(version = newVersion)

  }

  def default(method: Method, url: URL, body: Body = Body.empty): Request =
    ClientRequest(body, Headers.empty, method, url, Version.`HTTP/1.1`, Option.empty)

  def delete(url: URL): Request = default(Method.DELETE, url)

  def get(url: URL): Request = default(Method.GET, url)

  def options(url: URL): Request = default(Method.OPTIONS, url)

  def patch(body: Body, url: URL): Request = default(Method.PATCH, url, body)

  def post(body: Body, url: URL): Request = default(Method.POST, url, body)

  def put(body: Body, url: URL): Request = default(Method.PUT, url, body)

}
