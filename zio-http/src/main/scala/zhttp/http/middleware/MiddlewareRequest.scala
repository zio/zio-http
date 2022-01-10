package zhttp.http.middleware

import zhttp.http.headers.HeaderExtension
import zhttp.http.{Headers, Method, Request, URL}

case class MiddlewareRequest(
  private val request: Request,
) extends HeaderExtension[MiddlewareRequest] {
  self =>
  def method: Method   = request.method
  def url: URL         = request.url
  def headers: Headers = request.getHeaders

  /**
   * Returns the Headers object on the current type A
   */
  override def getHeaders: Headers = headers

  /**
   * Updates the current Headers with new one, using the provided update function passed.
   */
  override def updateHeaders(update: Headers => Headers): MiddlewareRequest =
    self.copy(request = request.updateHeaders(update))
}
