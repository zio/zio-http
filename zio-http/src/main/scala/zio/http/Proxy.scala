package zio.http
import io.netty.handler.proxy.HttpProxyHandler
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers

import java.net.InetSocketAddress
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Represents the connection to the forward proxy before running the request
 *
 * @param url:
 *   url address of the proxy server
 * @param credentials:
 *   credentials for the proxy server. Encodes credentials with basic auth and
 *   put under the 'proxy-authorization' header
 * @param headers:
 *   headers for the request to the proxy server
 */
final case class Proxy(
  url: URL,
  credentials: Option[Credentials] = None,
  headers: Headers = Headers.empty,
) { self =>

  def withUrl(url: URL): Proxy                         = self.copy(url = url)
  def withCredentials(credentials: Credentials): Proxy = self.copy(credentials = Some(credentials))
  def withHeaders(headers: Headers): Proxy             = self.copy(headers = headers)
}

object Proxy {
  val empty: Proxy = Proxy(URL.empty)
}
