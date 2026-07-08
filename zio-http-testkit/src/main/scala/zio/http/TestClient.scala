package zio.http

import java.util.concurrent.atomic.AtomicReference

import zio.blocks.endpoint.RoutePattern
import zio.http.ResultType._

/**
 * An in-process test double for a [[Client]]: lets tests register
 * [[Route]]/[[Routes]] behavior and a fallback handler for requests that don't
 * match any registered route, then exercises code that depends on a [[Client]]
 * without any real network I/O.
 *
 * @example
 *   {{{
 *   val client = TestClient.make()
 *   client.addRoute(Route(Method.GET / "hello", handler(Response.text("hi"))))
 *   client.send(Request.get(URL.root / "hello"))
 *   }}}
 */
final class TestClient private (
  private val routesRef: AtomicReference[Routes[Any]],
  private val fallbackRef: AtomicReference[Request => Response],
) extends Client {

  /**
   * Registers a new route. Routes added later take precedence over
   * previously-registered routes that match the same request.
   */
  def addRoute(route: Route[Any]): Unit =
    routesRef.updateAndGet(_ ++ Routes(route))

  /**
   * Registers new routes. Routes added later take precedence over
   * previously-registered routes that match the same request.
   */
  def addRoutes(routes: Routes[Any]): Unit =
    routesRef.updateAndGet(_ ++ routes)

  /**
   * Registers an exact request/response pair: `response` is only returned when
   * an incoming request's method, path, and headers match `expectedRequest`
   * (additional headers present on the real request are ignored).
   *
   * @example
   *   {{{
   *    client.addRequestResponse(Request.get(URL.root), Response.ok)
   *   }}}
   */
  def addRequestResponse(
    expectedRequest: Request,
    response: Response,
  ): Unit = {
    def matches(realRequest: Request): Boolean =
      // The way that the Client breaks apart and re-assembles the request prevents a straightforward
      //    expectedRequest == realRequest
      expectedRequest.url.path == realRequest.url.path &&
        expectedRequest.method == realRequest.method &&
        expectedRequest.headers.toList.toSet.forall(realRequest.headers.toList.toSet.contains)

    addRoute(
      Route(
        RoutePattern(expectedRequest.method, expectedRequest.url.path),
        Handler.fromRequest { (realRequest: Request) =>
          if (matches(realRequest)) response
          else
            throw new MatchError(
              s"TestClient received unexpected request: $realRequest (expected: $expectedRequest)",
            )
        },
      ),
    )
  }

  /**
   * Sets a fallback behaviour invoked when a request doesn't match any
   * registered route. Defaults to always returning [[Response.notFound]].
   *
   * @example
   *   {{{
   *   val failedRequests = new java.util.concurrent.ConcurrentLinkedQueue[Request]()
   *   client.setFallbackHandler { req => failedRequests.add(req); Response.notFound }
   *   }}}
   */
  def setFallbackHandler(fallback: Request => Response): Unit =
    fallbackRef.set(fallback)

  override def send(request: Request): Response =
    TestServer.dispatchOption(routesRef.get(), request).getOrElse(fallbackRef.get()(request))
}

object TestClient {

  /**
   * Creates a fresh `TestClient` with no registered routes and a "not found"
   * fallback.
   */
  def make(): TestClient =
    new TestClient(
      new AtomicReference(Routes.empty[Any]),
      new AtomicReference((_: Request) => Response.notFound),
    )
}
