package zio.http

import zio.test._
import zio.blocks.endpoint.RoutePattern

object RoutesV4Spec extends ZIOSpecDefault {

  private def mkRoute(pattern: RoutePattern[Unit]): Route[Any] =
    Route(pattern, Handler.succeed(Response.ok))

  def spec = suite("RoutesV4")(
    suite("Routes.empty")(
      test("has size 0") {
        assertTrue(Routes.empty[Any].size == 0)
      },
      test("toString mentions 0 routes") {
        val r = Routes.empty[Any]
        assertTrue(r.toString.contains("0 routes"))
      },
    ),
    suite("Routes single route")(
      test("size is 1") {
        val r = Routes(mkRoute(RoutePattern.GET))
        assertTrue(r.size == 1)
      },
      test("toString mentions 1 routes") {
        val r = Routes(mkRoute(RoutePattern.GET))
        assertTrue(r.toString.contains("1 routes"))
      },
    ),
    suite("Routes multiple routes")(
      test("two routes have size 2") {
        val r = Routes(mkRoute(RoutePattern.GET), mkRoute(RoutePattern.POST))
        assertTrue(r.size == 2)
      },
      test("three routes have size 3") {
        val r = Routes(
          mkRoute(RoutePattern.GET),
          mkRoute(RoutePattern.POST),
          mkRoute(RoutePattern.PUT),
        )
        assertTrue(r.size == 3)
      },
    ),
    suite("Routes concatenation ++")(
      test("empty ++ empty = empty") {
        val combined = Routes.empty[Any] ++ Routes.empty[Any]
        assertTrue(combined.size == 0)
      },
      test("single ++ empty = single") {
        val r1       = Routes(mkRoute(RoutePattern.GET))
        val combined = r1 ++ Routes.empty[Any]
        assertTrue(combined.size == 1)
      },
      test("empty ++ single = single") {
        val r2       = Routes(mkRoute(RoutePattern.POST))
        val combined = Routes.empty[Any] ++ r2
        assertTrue(combined.size == 1)
      },
      test("single ++ single = two") {
        val r1       = Routes(mkRoute(RoutePattern.GET))
        val r2       = Routes(mkRoute(RoutePattern.POST))
        val combined = r1 ++ r2
        assertTrue(combined.size == 2)
      },
      test("two ++ three = five") {
        val r1       = Routes(mkRoute(RoutePattern.GET), mkRoute(RoutePattern.POST))
        val r2       = Routes(
          mkRoute(RoutePattern.PUT),
          mkRoute(RoutePattern.DELETE),
          mkRoute(RoutePattern.PATCH),
        )
        val combined = r1 ++ r2
        assertTrue(combined.size == 5)
      },
    ),
    suite("Routes.fromIterable")(
      test("empty iterable produces empty routes") {
        val r = Routes.fromIterable(List.empty[Route[Any]])
        assertTrue(r.size == 0)
      },
      test("single-element iterable has size 1") {
        val r = Routes.fromIterable(List(mkRoute(RoutePattern.GET)))
        assertTrue(r.size == 1)
      },
      test("two-element iterable has size 2") {
        val r = Routes.fromIterable(
          List(mkRoute(RoutePattern.GET), mkRoute(RoutePattern.POST)),
        )
        assertTrue(r.size == 2)
      },
    ),
    suite("Routes @@ middleware")(
      test("applying Middleware.identity preserves size") {
        val routes = Routes(mkRoute(RoutePattern.GET), mkRoute(RoutePattern.POST))
        val result = routes @@ Middleware.identity[Any]
        assertTrue(result.size == routes.size)
      },
      test("applying middleware that adds a route increases size") {
        val extra  = mkRoute(RoutePattern.PATCH)
        val addOne = new Middleware[Any, Any] {
          def apply(routes: Routes[Any]): Routes[Any] =
            routes ++ Routes(extra)
        }
        val routes = Routes(mkRoute(RoutePattern.GET))
        val result = routes @@ addOne
        assertTrue(result.size == 2)
      },
    ),
    suite("Routes.toString")(
      test("starts with 'Routes('") {
        val r = Routes(mkRoute(RoutePattern.GET))
        assertTrue(r.toString.startsWith("Routes("))
      },
      test("ends with ')'") {
        val r = Routes(mkRoute(RoutePattern.GET))
        assertTrue(r.toString.endsWith(")"))
      },
    ),
  )
}
