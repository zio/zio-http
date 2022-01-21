package zhttp.service.client.experimental

import zhttp.http.{Request, Response}
import zio.stream.ZStream
import zio.{Task, ZIO}

class ZCInterface {

    /** Submits a request, and provides a callback to process the response.
     *
     * @param req The request to submit
     * @param f   A callback for the response to req.  The underlying HTTP connection
     *            is maintained by internal connection manager and kept alive or termainated
     *            based on configurations. In case of connection getting terminated attempt to
     *            read the response will result in error.
     * @return The result of applying f to the response to req
     */
    def run[A](req: Request)(f: Response => Task[A]): Task[A] = ???

    /** Submits a request, and provides a callback to process the response.
     *
     * @param req An effect (???) of the request to submit
     * @param f   A callback for the response to req.  The underlying HTTP connection
     *            is maintained by internal connection manager and kept alive or termainated
     *            based on configuratons. In case of connection getting terminated attempt to
     *            read the response will result in error.
     * @return The result of applying f to the response to req
     */
//    def run[A](req: ZIO[?,?,Request])(f: Response => Task[A]): Task[A] = ???


  /**
   *  TBD
   * @param req
   * @return
   */
  def stream(req: Request): ZStream[Any,Throwable,Response] = ???

  /**
   * TBD
   *
   * @param req
   * @param f
   * @tparam A
   * @return
   */
  def streaming[A](req: Request)(f: Response => ZStream[Any,Throwable,A]): ZStream[Any,Throwable,A] = ???

  /**
   * TBD
   * Submits a request and decodes the response on success.  On failure, the
   * status code is returned.  The underlying HTTP connection is closed at the
   * completion of the decoding.
    */
//  def decode[A](req: Request)(implicit d: EntityDecoder): Task[A] = ???

//  def runOr[A](req: Request)(onError: Response => Throwable)(implicit d: SomeEntityDecoder): A = ???

  /**
 * Submits a GET request to the specified URI and decodes the response on
 * success.  On failure, the status code is returned.  The underlying HTTP
 * connection is closed at the completion of the decoding.
    */

  /**
   * Submits a GET request to the specified URI
   * @param uri The URI to GET
   */
  def run(url: zhttp.http.URL): Task[Response] = ???

  def run(urlString: String): Task[Response] = ???

  def run(request: zhttp.http.Request): Task[Response] = ???

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


  /** Submits a request and returns true if and only if the response status is
 * successful */
  def successful(req: Request): Task[Boolean] = ???

//  /** Submits a request and returns true if and only if the response status is
// * successful */
//  def successful(req: F[Request[F]]): F[Boolean] =

//  /**
// * Submits a request and decodes the response on success.  On failure, the
// * status code is returned.  The underlying HTTP connection is closed at the
// * completion of the decoding.
//    */
//  def get[A](s: String)(f: Response[F] => F[A]): F[A] =
//    Uri.fromString(s).fold(F.raiseError, uri => get(uri)(f))


}
