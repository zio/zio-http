package zhttp.service.client.experimental

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames, HttpHeaderValues, HttpVersion}
import zhttp.http.{HTTP_CHARSET, Request, Response, URL}
import zhttp.service.HttpMessageCodec
import zhttp.service.client.experimental.ZClient.Config
import zio.{Task, ZIO}
import zio.stream.ZStream

case class DefaultZClient(
                           settings: Config,
                           connectionManager: ZConnectionManager,
                         ) extends HttpMessageCodec {
  def run(req: ReqParams): Task[Resp] =
    for {
      jReq    <- Task(encodeClientParams(HttpVersion.HTTP_1_1, req))
      channel <- connectionManager.fetchConnection(jReq)
      prom    <- zio.Promise.make[Throwable, Resp]
      _       <- ZIO.effect(connectionManager.currentExecRef += (channel -> (prom, jReq)))
      _       <- ZIO.effect { channel.pipeline().fireChannelActive() }
      resp    <- prom.await
    } yield resp

  def run(req: Request): Task[Resp] =
    for {
      jReq    <- encodeClientParams(req)
      channel <- connectionManager.fetchConnection(jReq)
      prom    <- zio.Promise.make[Throwable, Resp]
      _       <- ZIO.effect(connectionManager.currentExecRef += (channel -> (prom, jReq)))
      _       <- ZIO.effect { channel.pipeline().fireChannelActive() }
      resp    <- prom.await
    } yield resp

  def run(str: String): Task[Resp] = {
    for {
      url <- ZIO.fromEither(URL.fromString(str))
      req = ReqParams(url = url)
      res <- run(req)
    } yield res
  }

  def encodeClientParams(jVersion: HttpVersion, req: ReqParams): FullHttpRequest = {
    val method      = req.method.asHttpMethod
    val uri         = req.url.encode
    val content     = req.getBodyAsString match {
      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => Unpooled.EMPTY_BUFFER
    }
    val headers     = req.getHeaders.encode.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    val jReq        = new DefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }

  def encodeClientParams(req: Request): Task[FullHttpRequest] = {
    val jVersion = HttpVersion.HTTP_1_1
    val method      = req.method.asHttpMethod
    val uri         = req.url.encode
    for {
      reqContent <- req.getBodyAsString
      content = Unpooled.copiedBuffer(reqContent,HTTP_CHARSET)
    } yield (new DefaultFullHttpRequest(jVersion, method, uri, content))
  }

  /**
   * Submits a GET request to the specified URI
   * @param uri
   *   The URI to GET
   */
  def run(url: zhttp.http.URL): Task[Response] = ???

  /**
   * Submits a GET request to the specified URI and decodes the response on success. On failure, the status code is
   * returned. The underlying HTTP connection is closed at the completion of the decoding.
   */



  //  /**
  // * Submits a request and decodes the response, regardless of the status code.
  // * The underlying HTTP connection is closed at the completion of the
  // * decoding.
  //    */
  //  def fetchAs[A](req: F[Request[F]])(implicit d: EntityDecoder[F, A]): F[A] =
  //    req.flatMap(fetchAs(_)(d))

  /** Submits a request and returns the response status */
  def status(req: Request): Task[zhttp.http.Status] = ???

  //  /** Submits a request and returns the response status */
  //  def status(req: F[Request[F]]): F[Status] =

  /**
   * Submits a request and returns true if and only if the response status is successful
   */
  def successful(req: Request): Task[Boolean] = ???

  // ****************** APIs below need more clarity *********************

  /**
   * Submits a request and decodes the response on success
   * use zio json decoder to get custom type
   */
  //    def decodedResponse[A](req: Task[Request])(implicit decoder: JsonDecoder[A]): Task[A] =
  //      ???

  /**
   * Submits a request, and provides a callback to process the response.
   *
   * @param req
   *   The request to submit
   * @param f
   *   A callback for the response to req.
   *   The underlying HTTP connection is maintained by internal connection manager
   *   and kept alive or terminated based on configurations.
   *   In case of connection getting terminated attempt to read
   *   the response will result in error.
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
   *   A callback for the response to req.
   *   The underlying HTTP connection is maintained by internal connection manager
   *   and kept alive or terminated based on configurations.
   *   In case of connection getting terminated attempt to read
   *   the response will result in error.
   * @return
   *   The result of applying f to the response to req
   */
  //    def run[A](req: ZIO[?,?,Request])(f: Response => Task[A]): Task[A] = ???

  /**
   * TBD
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

