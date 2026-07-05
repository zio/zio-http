package zio.http

import zio.test._
import zio.blocks.endpoint.RoutePattern

object MiddlewareV4Spec extends ZIOSpecDefault {

  private def mkRoutes(n: Int): Routes[Any] = {
    val patterns = List(
      RoutePattern.GET,
      RoutePattern.POST,
      RoutePattern.PUT,
      RoutePattern.DELETE,
      RoutePattern.PATCH,
    )
    val routes   = patterns.take(n).map(p => Route(p, Handler.succeed(Response.ok)))
    Routes.fromIterable(routes)
  }

  def spec = suite("MiddlewareV4")(
    suite("Middleware.identity")(
      test("returns empty routes unchanged") {
        val result = Middleware.identity[Any](Routes.empty[Any])
        assertTrue(result.size == 0)
      },
      test("returns single-route routes unchanged") {
        val routes = mkRoutes(1)
        val result = Middleware.identity[Any](routes)
        assertTrue(result.size == 1)
      },
      test("returns multi-route routes unchanged") {
        val routes = mkRoutes(3)
        val result = Middleware.identity[Any](routes)
        assertTrue(result.size == 3)
      },
      test("identity via @@ operator preserves size") {
        val routes = mkRoutes(2)
        val result = routes @@ Middleware.identity[Any]
        assertTrue(result.size == routes.size)
      },
    ),
    suite("Middleware.andThen")(
      test("m1.andThen(m2) calls m1 first, then m2") {
        val callOrder                = new scala.collection.mutable.ArrayBuffer[Int]()
        val m1: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] = {
            callOrder += 1
            routes
          }
        }
        val m2: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] = {
            callOrder += 2
            routes
          }
        }
        val composed                 = m1.andThen(m2)
        composed(mkRoutes(1))
        assertTrue(callOrder.toList == List(1, 2))
      },
      test("m1.andThen(m2) applies both middlewares") {
        var m1Applied                = false
        var m2Applied                = false
        val m1: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] = { m1Applied = true; routes }
        }
        val m2: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] = { m2Applied = true; routes }
        }
        m1.andThen(m2)(mkRoutes(1))
        assertTrue(m1Applied && m2Applied)
      },
      test("andThen result preserves route count") {
        val composed = Middleware.identity[Any].andThen(Middleware.identity[Any])
        val routes   = mkRoutes(3)
        val result   = composed(routes)
        assertTrue(result.size == 3)
      },
    ),
    suite("Custom middleware")(
      test("middleware that prepends a route increases size by 1") {
        val extraRoute                     = Route(RoutePattern.OPTIONS, Handler.succeed(Response.ok))
        val addRoute: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] =
            Routes(extraRoute) ++ routes
        }
        val base                           = mkRoutes(2)
        val result                         = base @@ addRoute
        assertTrue(result.size == 3)
      },
      test("middleware that filters to empty still applies") {
        val clearAll: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] = Routes.empty[Any]
        }
        val base                           = mkRoutes(3)
        val result                         = base @@ clearAll
        assertTrue(result.size == 0)
      },
      test("composed middlewares chain transformations") {
        val addOne: Middleware[Any, Any] = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] =
            routes ++ Routes(Route(RoutePattern.HEAD, Handler.succeed(Response.ok)))
        }
        val base                         = mkRoutes(1)
        val result                       = base @@ addOne.andThen(addOne)
        assertTrue(result.size == 3)
      },
    ),
  )
}
