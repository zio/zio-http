package zio.http

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object AutoGenerateHeadRoutesSpec extends ZIOHttpSpec {

  def spec = suite("AutoGenerateHeadRoutesSpec")(
    suite("when autoGenerateHeadRoutes is enabled")(
      test("GET route automatically responds to HEAD requests") {
        val routes = Routes(
          Method.GET / "users" -> handler(Response.text("user list")),
        )

        for {
          getResponse  <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.get(URL(Path.root / "users")))
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "users")))
        } yield assertTrue(
          getResponse.status == Status.Ok,
          headResponse.status == Status.Ok,
        )
      },
      test("GET route with path parameters responds to HEAD") {
        val routes = Routes(
          Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
            Response.text(s"User $id")
          },
        )

        for {
          getResponse  <- routes
            .toHandler(autoGenerateHeadRoutes = true)
            .apply(Request.get(URL(Path.root / "users" / "42")))
          headResponse <- routes
            .toHandler(autoGenerateHeadRoutes = true)
            .apply(Request.head(URL(Path.root / "users" / "42")))
          getBody      <- getResponse.body.asString
          headBody     <- headResponse.body.asString
        } yield assertTrue(
          getResponse.status == Status.Ok,
          getBody == "User 42",
          headResponse.status == Status.Ok,
          headBody.isEmpty,
        )
      },
      test("explicit HEAD route takes precedence over auto-generated") {
        val routes = Routes(
          Method.GET / "users"  -> handler(Response.text("GET response")),
          Method.HEAD / "users" -> handler(
            Response.text("HEAD response").addHeader(Header.Custom("X-Custom", "explicit")),
          ),
        )

        for {
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "users")))
        } yield {
          val customHeader = headResponse.headers.get("X-Custom")
          assertTrue(
            headResponse.status == Status.Ok,
            customHeader.contains("explicit"),
          )
        }
      },
      test("POST route does not generate HEAD route") {
        val routes = Routes(
          Method.POST / "users" -> handler(Response.text("created")),
        )

        for {
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "users")))
        } yield assertTrue(
          headResponse.status == Status.NotFound,
        )
      },
      test("nested routes with GET generate HEAD routes") {
        val routes = Routes(
          Method.GET / "api" / "v1" / "users" -> handler(Response.text("users")),
        )

        for {
          headResponse <- routes
            .toHandler(autoGenerateHeadRoutes = true)
            .apply(Request.head(URL(Path.root / "api" / "v1" / "users")))
          headBody     <- headResponse.body.asString
        } yield assertTrue(
          headResponse.status == Status.Ok,
          headBody.isEmpty,
        )
      },
      test("multiple GET routes all generate HEAD routes") {
        val routes = Routes(
          Method.GET / "users"    -> handler(Response.text("users")),
          Method.GET / "products" -> handler(Response.text("products")),
          Method.GET / "orders"   -> handler(Response.text("orders")),
        )

        for {
          head1 <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "users")))
          head2 <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "products")))
          head3 <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "orders")))
          body1 <- head1.body.asString
          body2 <- head2.body.asString
          body3 <- head3.body.asString
        } yield assertTrue(
          head1.status == Status.Ok && body1.isEmpty,
          head2.status == Status.Ok && body2.isEmpty,
          head3.status == Status.Ok && body3.isEmpty,
        )
      },
      test("HEAD request preserves Content-Length header") {
        val routes = Routes(
          Method.GET / "data" -> handler(Response.text("Hello World")),
        )

        for {
          getResponse  <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.get(URL(Path.root / "data")))
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = true).apply(Request.head(URL(Path.root / "data")))
          getLength  = getResponse.headers.get(Header.ContentLength)
          headLength = headResponse.headers.get(Header.ContentLength)
          headBody <- headResponse.body.asString
        } yield assertTrue(
          getLength.isDefined,
          headLength.isDefined,
          getLength == headLength,
          headBody.isEmpty,
        )
      },
    ),
    suite("when autoGenerateHeadRoutes is disabled")(
      test("GET route does not respond to HEAD requests") {
        val routes = Routes(
          Method.GET / "users" -> handler(Response.text("user list")),
        )

        for {
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = false).apply(Request.head(URL(Path.root / "users")))
        } yield assertTrue(
          headResponse.status == Status.NotFound,
        )
      },
      test("explicit HEAD route still works") {
        val routes = Routes(
          Method.GET / "users"  -> handler(Response.text("GET response")),
          Method.HEAD / "users" -> handler(Response.text("HEAD response")),
        )

        for {
          headResponse <- routes.toHandler(autoGenerateHeadRoutes = false).apply(Request.head(URL(Path.root / "users")))
        } yield assertTrue(
          headResponse.status == Status.Ok,
        )
      },
    ),
    suite("ANY routes")(
      test("ANY route responds to HEAD with no body") {
        val routes = Routes(
          RoutePattern.any -> handler { (path: Path, req: Request) =>
            Response.text(s"Method: ${req.method}, Path: $path")
          },
        )

        for {
          getResponse  <- routes.toHandler.apply(Request.get(URL(Path.root / "test")))
          headResponse <- routes.toHandler.apply(Request.head(URL(Path.root / "test")))
          getBody      <- getResponse.body.asString
          headBody     <- headResponse.body.asString
        } yield assertTrue(
          getResponse.status == Status.Ok,
          getBody.contains("Method: GET"),
          headResponse.status == Status.Ok,
          headBody.isEmpty,
        )
      },
      test("ANY route with specific path responds to HEAD") {
        val routes = Routes(
          Method.ANY / "resource" -> handler(Response.text("resource content")),
        )

        for {
          getResponse  <- routes.toHandler.apply(Request.get(URL(Path.root / "resource")))
          headResponse <- routes.toHandler.apply(Request.head(URL(Path.root / "resource")))
          getBody      <- getResponse.body.asString
          headBody     <- headResponse.body.asString
        } yield assertTrue(
          getResponse.status == Status.Ok,
          getBody == "resource content",
          headResponse.status == Status.Ok,
          headBody.isEmpty,
        )
      },
    ),
    suite("integration with server config")(
      test("server with autoGenerateHeadRoutes enabled") {
        val routes = Routes(
          Method.GET / "test" -> handler(Response.text("test content")),
        )

        val config = Server.Config.default.autoGenerateHeadRoutes(true)

        for {
          response <- routes.toHandler(config.autoGenerateHeadRoutes).apply(Request.head(URL(Path.root / "test")))
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.isEmpty,
        )
      },
      test("server with autoGenerateHeadRoutes disabled") {
        val routes = Routes(
          Method.GET / "test" -> handler(Response.text("test content")),
        )

        val config = Server.Config.default.autoGenerateHeadRoutes(false)

        for {
          response <- routes.toHandler(config.autoGenerateHeadRoutes).apply(Request.head(URL(Path.root / "test")))
        } yield assertTrue(
          response.status == Status.NotFound,
        )
      },
    ),
  )
}
