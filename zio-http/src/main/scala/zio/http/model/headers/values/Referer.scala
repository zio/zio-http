package zio.http.model.headers.values

import zio.http.URL

import scala.util.Try

/**
 * The Referer HTTP request header contains the absolute or partial address from
 * which a resource has been requested. The Referer header allows a server to
 * identify referring pages that people are visiting from or where requested
 * resources are being used. This data can be used for analytics, logging,
 * optimized caching, and more.
 *
 * When you click a link, the Referer contains the address of the page that
 * includes the link. When you make resource requests to another domain, the
 * Referer contains the address of the page that uses the requested resource.
 *
 * The Referer header can contain an origin, path, and querystring, and may not
 * contain URL fragments (i.e. #section) or username:password information. The
 * request's referrer policy defines the data that can be included. See
 * Referrer-Policy for more information and examples.
 */
sealed trait Referer

object Referer {

  /**
   * The Location header contains URL of the new Resource
   */
  final case class ValidReferer(url: URL) extends Referer

  /**
   * The URL header value is invalid.
   */
  case object InvalidReferer extends Referer

  def fromReferer(url: Referer): String = {
    url match {
      case ValidReferer(url) => url.toJavaURL.fold("")(_.toString())
      case InvalidReferer    => ""
    }

  }

  def toReferer(value: String): Referer = {
    URL.fromString(value) match {
      case Left(_)                                              => InvalidReferer
      case Right(url) if url.host.isEmpty || url.scheme.isEmpty => InvalidReferer
      case Right(url)                                           => ValidReferer(url)
    }
  }
}
