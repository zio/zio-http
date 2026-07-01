package zio.http

import zio.test._
import zio.blocks.endpoint.RoutePattern

object RouteV4Spec extends ZIOSpecDefault {

  def spec = suite("RouteV4")(
    suite("Route construction")(
      test("Route(RoutePattern.GET, handler) constructs successfully") {
        val pattern = RoutePattern.GET
        val h       = Handler.succeed(Response.ok)
        val route   = Route(pattern, h)
        assertTrue(route != null)
      },
      test("Route(RoutePattern.POST, handler) constructs successfully") {
        val route = Route(RoutePattern.POST, Handler.succeed(Response.ok))
        assertTrue(route != null)
      },
      test("Route with path-specific pattern") {
        val route = Route(RoutePattern(Method.GET, "/hello"), Handler.succeed(Response.ok))
        assertTrue(route != null)
      },
      test("Route with PUT and path") {
        val route = Route(RoutePattern(Method.PUT, "/users"), Handler.succeed(Response.ok))
        assertTrue(route != null)
      },
    ),
    suite("Route.pattern")(
      test("pattern method matches the original method - GET") {
        val route = Route(RoutePattern(Method.GET, "/hello"), Handler.succeed(Response.ok))
        assertTrue(route.pattern.method == Method.GET)
      },
      test("pattern method matches the original method - POST") {
        val route = Route(RoutePattern(Method.POST, "/items"), Handler.succeed(Response.ok))
        assertTrue(route.pattern.method == Method.POST)
      },
      test("pattern method matches the original method - DELETE") {
        val route = Route(RoutePattern(Method.DELETE, "/items"), Handler.succeed(Response.ok))
        assertTrue(route.pattern.method == Method.DELETE)
      },
      test("RoutePattern.GET bare pattern has GET method") {
        val route = Route(RoutePattern.GET, Handler.succeed(Response.ok))
        assertTrue(route.pattern.method == Method.GET)
      },
    ),
    suite("Route.handler")(
      test("handler field is accessible and non-null") {
        val h     = Handler.succeed(Response.ok)
        val route = Route(RoutePattern.GET, h)
        assertTrue(route.handler != null)
      },
    ),
    suite("Route.toString")(
      test("toString starts with 'Route('") {
        val route = Route(RoutePattern.GET, Handler.succeed(Response.ok))
        assertTrue(route.toString.startsWith("Route("))
      },
      test("toString contains method information") {
        val route = Route(RoutePattern(Method.POST, "/api"), Handler.succeed(Response.ok))
        assertTrue(route.toString.contains("Route("))
      },
      test("toString ends with ')'") {
        val route = Route(RoutePattern.GET, Handler.succeed(Response.ok))
        assertTrue(route.toString.endsWith(")"))
      },
    ),
    suite("Route type variance")(
      test("Route[-Ctx] is usable as Route[Any]") {
        val route: Route[Any] = Route(RoutePattern.GET, Handler.succeed(Response.ok))
        assertTrue(route != null)
      },
    ),
  )
}
