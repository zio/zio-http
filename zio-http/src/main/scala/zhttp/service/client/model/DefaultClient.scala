package zhttp.service.client.model

import zhttp.http.Method.GET
import zhttp.http._
import zhttp.service.Client.{Attribute, ClientRequest, ClientResponse, Config}
import zhttp.service.HttpMessageCodec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.transport.ClientConnectionManager
import zio.stream.ZStream
import zio.{Task, ZIO}

case class DefaultClient(
  settings: Config,
  connectionManager: ClientConnectionManager,
) extends HttpMessageCodec {

  def run(req: ClientRequest): Task[ClientResponse] =
    for {
      jReq    <- encode(req)
      channel <- connectionManager.fetchConnection(jReq, req)
      prom    <- zio.Promise.make[Throwable, ClientResponse]
      // the part below can be moved to connection manager.
      _       <- ZIO.effect(
        connectionManager.connectionState.currentAllocatedChannels += (channel -> ConnectionRuntime(prom, jReq)),
      )
      // trigger the channel, triggering inbound event propagation
      // should be part of connection manager?
      _       <- ZIO.effect {
        channel.pipeline().fireChannelActive()
      }
      resp    <- prom.await
    } yield resp

  def run(
    str: String,
    method: Method = GET,
    headers: Headers = Headers.empty,
    content: HttpData = HttpData.empty,
    ssl: Option[ClientSSLOptions] = None,
  ): Task[ClientResponse] = {
    for {
      url <- ZIO.fromEither(URL.fromString(str))
      req = ClientRequest(url, method, headers, content, attribute = Attribute(ssl = ssl))
      res <- run(req)
    } yield res
  }

  /**
   * Submits a GET request to the specified URI
   *
   * @param uri
   *   The URI to GET
   */
  def run(url: URL): Task[Response] = ???

  /**
   * Submits a GET request to the specified URI and decodes the response on
   * success. On failure, the status code is returned. The underlying HTTP
   * connection is closed at the completion of the decoding.
   */

  //  /**
  // * Submits a request and decodes the response, regardless of the status code.
  // * The underlying HTTP connection is closed at the completion of the
  // * decoding.
  //    */
  //  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
  //    req.flatMap(fetchAs(_)(d))

  /** Submits a request and returns the response status */
  def status(req: Request): Task[Status] = ???

  //  /** Submits a request and returns the response status */
  //  def status(req: F[Request[F]]): F[Status] =

  /**
   * Submits a request and returns true if and only if the response status is
   * successful
   */
  def successful(req: Request): Task[Boolean] = ???

  // ****************** APIs below need more clarity *********************

  /**
   * Submits a request and decodes the response on success use zio json decoder
   * to get custom type
   */
  //    def decodedResponse[A](req: Task[Request])(implicit decoder: JsonDecoder[A]): Task[A] =
  //      ???

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
   * TBD Key API
   *
   * @param req
   * @return
   */
  def stream(req: Request): ZStream[Any, Throwable, Response] = ???

  /**
   * streaming with callback TBD
   *
   * @param req
   * @param f
   * @tparam A
   * @return
   */
  def streaming[A](req: Request)(f: Response => ZStream[Any, Throwable, A]): ZStream[Any, Throwable, A] = ???

  //  def runOr[A](req: Request)(onError: Response => Throwable)(implicit d: SomeEntityDecoder): A = ???

  //  /** Submits a request and returns true if and only if the response status is
  // * successful */
  //  def success(req: UIO[Request]): Task[Boolean] =

  //  /**
  // * Submits a request and decodes the response on success.  On failure, the
  // * status code is returned.  The underlying HTTP connection is closed at the
  // * completion of the decoding.
  //    */
  //  def get[A](s: String)(f: Task[Response] => Task[A]): Task[A] =
  //    Uri.fromString(s).fold(_ => ..., uri => run(uri)(f))

}
