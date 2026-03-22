package zio.http

import zio._
import zio.test._

object TracingMiddlewareSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Middleware.tracing")(
      test("creates span with default name from route pattern") {
        val routes = Routes(
          Method.GET / "users" / string("id") ->
            handler { (_: String, _: Request) =>
              ZIO.log("handled") *> ZIO.succeed(Response.ok)
            },
        ) @@ Middleware.tracing()
        for {
          _    <- routes.runZIO(Request.get(url"/users/123"))
          logs <- ZTestLogger.logOutput
          logOpt = logs.find(_.message() == "handled")
        } yield assertTrue(
          logOpt.exists(_.spans.exists(_.label == "GET /users/{id}")),
        )
      },
      test("adds HTTP annotations") {
        val routes = Routes(
          Method.POST / "api" / "items" ->
            handler { (_: Request) =>
              ZIO.log("handled") *> ZIO.succeed(Response.status(Status.Created))
            },
        ) @@ Middleware.tracing()
        for {
          _    <- routes.runZIO(Request.post("/api/items", Body.empty))
          logs <- ZTestLogger.logOutput
          logOpt = logs.find(_.message() == "handled")
        } yield assertTrue(
          logOpt.exists(_.annotations.get("http.method").contains("POST")),
          logOpt.exists(_.annotations.get("http.route").contains("/api/items")),
          logOpt.exists(_.annotations.get("http.target").contains("/api/items")),
        )
      },
      test("supports custom span name") {
        val routes = Routes
          .singleton(handler(ZIO.log("handled") *> ZIO.succeed(Response.ok)))
          .@@(Middleware.tracing(spanName = (_, req) => s"custom-${req.method.render}"))
        for {
          _    <- routes.runZIO(Request.get(url"/"))
          logs <- ZTestLogger.logOutput
          logOpt = logs.find(_.message() == "handled")
        } yield assertTrue(
          logOpt.exists(_.spans.exists(_.label == "custom-GET")),
        )
      },
      test("includes query string in http.target") {
        val routes = Routes(
          Method.GET / "search" ->
            handler { (_: Request) =>
              ZIO.log("handled") *> ZIO.succeed(Response.ok)
            },
        ) @@ Middleware.tracing()
        for {
          _    <- routes.runZIO(Request.get(url"/search?q=hello&page=1"))
          logs <- ZTestLogger.logOutput
          logOpt = logs.find(_.message() == "handled")
        } yield assertTrue(
          logOpt.exists(_.annotations.get("http.target").contains("/search?q=hello&page=1")),
        )
      },
    )
}
