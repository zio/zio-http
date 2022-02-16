package zhttp.service.client.domain

import io.netty.buffer.ByteBuf
import zhttp.http.Method.GET
import zhttp.http._
import zhttp.service.Client.{Attribute, ClientRequest, ClientResponse}
import zhttp.service.HttpMessageCodec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.transport.ClientConnectionManager
import zio.stream.ZStream
import zio.{Task, ZIO}

case class DefaultClient(
  connectionManager: ClientConnectionManager,
) extends HttpMessageCodec {

  // methods for compatibility with existing client use
  def run(req: ClientRequest): Task[ClientResponse] = for {
    jReq <- encode(req)
//    reqKey <- connectionManager.getRequestKey(jReq, req)
    prom <- zio.Promise.make[Throwable, ClientResponse]
    _    <- connectionManager.fetchConnection(jReq, req, prom)
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

  /**
   * Submits a GET request to the specified zhttp URL
   *
   * @param url
   *   The URI to GET
   */
  def run(url: URL): Task[Response] = ???

  /** Submits a request and returns the response status */
  def status(req: Request): Task[Status] = ???

  /** Submits a request with effect and returns the response status */
  def status(req: Task[Request]): Task[Status] = ???

  /**
   * Submits a request and returns true if and only if the response status is
   * successful, may be used for tests
   */
  def succeed(req: Request): Task[Boolean] = ???

  // ****************** APIs below need more clarity *********************

  /**
   * Submits a request, and provides a callback to process the response.
   *
   * @param req
   *   The request to submit
   * @param f
   *   A callback for the response to req. The underlying HTTP connection is
   *   maintained by internal connection manager and kept alive or terminated
   *   based on configurations. In case of connection getting terminated attempt
   *   to read the response will result in error.
   * @return
   *   The result of applying f to the response to req
   */
  def run[A](req: Request)(f: Response => Task[A]): Task[A] = ???

  /**
   * Submits a request, and provides a callback to process the response.
   *
   * @param req
   *   An effect (???) of the request to submit
   * @param f
   *   A callback for the response to req. The underlying HTTP connection is
   *   maintained by internal connection manager and kept alive or terminated
   *   based on configurations. In case of connection getting terminated attempt
   *   to read the response will result in error.
   * @return
   *   The result of applying f to the response to req
   */
  //    def run[A](req: ZIO[?,?,Request])(f: Response => Task[A]): Task[A] = ???

  /**
   * TBD or stream(req: Request): ZStream[Any,Throwable, ???]
   * @param req
   * @return
   */
  def stream(req: Request): ZStream[Any, Throwable, ByteBuf] = ???

  /**
   * streaming with callback TBD
   *
   * @param req
   * @param f
   * @tparam A
   * @return
   */
  def streaming[A](req: Request)(f: Response => ZStream[Any, Throwable, A]): ZStream[Any, Throwable, A] = ???

  // more APIs to follow

}
