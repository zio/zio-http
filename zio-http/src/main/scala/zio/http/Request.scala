package zio.http

import zio.http.model._
import zio.http.model.headers._

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

  def updatePath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Request =
    self.copy(headers = update(self.headers))
}

object Request {

  def default(method: Method, url: URL, body: Body = Body.empty) =
    Request(body, Headers.empty, method, url, Version.`HTTP/1.1`, Option.empty)

  def delete(url: URL): Request = default(Method.DELETE, url)

  def get(url: URL): Request = default(Method.GET, url)

  def options(url: URL): Request = default(Method.OPTIONS, url)

  def patch(body: Body, url: URL): Request = default(Method.PATCH, url, body)

  def post(body: Body, url: URL): Request = default(Method.POST, url, body)

  def put(body: Body, url: URL): Request = default(Method.PUT, url, body)

}
