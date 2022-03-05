package zhttp.service.client.domain

import zhttp.http.Method.GET
import zhttp.http._
import zhttp.service.Client.{Attribute, ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.transport.ClientConnectionManager
import zio.{Task, ZIO}

/**
 * Concrete Client instance holding a reference to connection manager Expose
 * multiple run methods
 * @param connectionManager
 */
case class DefaultClient(
  connectionManager: ClientConnectionManager,
) {

  // methods for compatibility with existing client use
  def run(req: ClientRequest): Task[ClientResponse] = for {
//    reqKey <- connectionManager.getRequestKey(req)
    prom <- zio.Promise.make[Throwable, ClientResponse]
    _    <- connectionManager.fetchConnection(req, prom)
    resp <- prom.await
    _    <- prom.complete(Task(resp))
  } yield resp

  // methods for compatibility with existing client use
  def run(
    str: String,
    method: Method = GET,
    headers: Headers = Headers.empty,
    content: HttpData = HttpData.empty,
    ssl: Option[ClientSSLOptions] = None,
  ): Task[ClientResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(str))
      req = ClientRequest(url, method, headers, content, attribute = Attribute(ssl = ssl))
      res <- run(req)
    } yield res
}
