package zio.http

/**
 * Synchronous HTTP client backed by Project Loom virtual threads.
 *
 * [[send]] is a blocking call that returns a [[Response]] directly. Callers
 * run on virtual threads so the underlying platform thread is not pinned while
 * waiting for I/O. There are no ZIO types on this interface; integration with
 * ZIO effect systems lives in `zio-http-zio`.
 */
trait Client {

  /**
   * Sends `request` and returns the server's response.
   *
   * Blocks the calling (virtual) thread until a complete response is received
   * or an error occurs. Throws on transport-level or protocol-level failure.
   */
  def send(request: Request): Response

  def @@(middleware: ClientMiddleware): Client =
    middleware(this)
}
