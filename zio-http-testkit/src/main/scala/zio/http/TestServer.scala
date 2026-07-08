package zio.http

import java.util.concurrent.atomic.AtomicReference

import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.scope.{Scope => BlocksScope}
import zio.http.ResultType._

/**
 * An in-process, in-memory test double for exercising [[Route]]/[[Routes]]
 * definitions without binding any real server socket.
 *
 * The current `zio-http` [[Client]]/[[Handler]] APIs are synchronous (there are
 * no ZIO types on either interface), so `TestServer` dispatches requests
 * directly against its mutable route table on the calling thread.
 *
 * Route matching is a linear scan over the registered routes in
 * most-recently-added-first order (rather than the
 * [[zio.blocks.endpoint.RouteTree]] trie the real server uses), so that a route
 * registered for the bare root path (an empty
 * [[zio.blocks.endpoint.PathCodec]], which `RouteTree` cannot index) is still
 * matchable, and so that re-registering the same pattern makes the newest
 * registration win.
 *
 * @example
 *   {{{
 *   val server = TestServer.make()
 *   server.addRoute(Route(Method.GET / "hello", handler(Response.text("hi"))))
 *   val response = server.client.send(Request.get(URL.root / "hello"))
 *   }}}
 */
final class TestServer private (private val routesRef: AtomicReference[Routes[Any]]) {

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
   *   server.addRequestResponse(Request.get(URL.root.port(port)), Response.ok)
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
        expectedRequest.headers.toList.forall { case (name, value) => realRequest.headers.rawGet(name).contains(value) }

    addRoute(
      Route(
        RoutePattern(expectedRequest.method, expectedRequest.url.path),
        Handler.fromRequest { (realRequest: Request) =>
          if (matches(realRequest)) response else Response.notFound
        },
      ),
    )
  }

  /**
   * A [[Client]] that dispatches directly against this server's current route
   * table, without any real network I/O.
   */
  val client: Client = new Client {
    override def send(request: Request): Response =
      TestServer.dispatch(routesRef.get(), request)
  }
}

object TestServer {

  /** Creates a fresh `TestServer` with no registered routes. */
  def make(): TestServer = new TestServer(new AtomicReference(Routes.empty[Any]))

  private[http] def dispatch(routes: Routes[Any], request: Request): Response =
    dispatchOption(routes, request).getOrElse(Response.notFound)

  /** Returns `None` when no registered route matches `request` at all. */
  private[http] def dispatchOption(routes: Routes[Any], request: Request): Option[Response] = {
    val method     = request.method
    val path       = request.url.path
    val firstMatch = routes.routes.reverseIterator
      .flatMap(route => route.pattern.decode(method, path).toOption.map(route -> _))
      .nextOption()

    firstMatch.map { case (route, vars) =>
      val openScope = BlocksScope.global.open()
      try toResponse(invokeHandler(route, request, vars, openScope.scope))
      finally openScope.close().orThrow()
    }
  }

  private def invokeHandler(
    route: Route[Any],
    request: Request,
    vars: Any,
    scope: BlocksScope,
  ): Response | Halt =
    try route.handler.handle(request, Context.empty, vars, scope)
    catch {
      case _: Throwable => Response.internalServerError
    }

  private def toResponse(result: Response | Halt): Response = {
    val value = (result: Any) match {
      case left: scala.util.Left[_, _]   => left.value
      case right: scala.util.Right[_, _] => right.value
      case x                             => x
    }
    value match {
      case response: Response => response
      case halt: Halt         => halt.response
    }
  }
}
