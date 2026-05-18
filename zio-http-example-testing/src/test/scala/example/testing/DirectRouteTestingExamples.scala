package example.testing

import zio._
import zio.http._
import zio.test._

/**
 * Examples of direct route testing patterns.
 *
 * Direct route testing invokes routes as pure functions without any server
 * infrastructure. This is the fastest way to test and is ideal for unit testing
 * individual handlers.
 */
object DirectRouteTestingExamples extends ZIOSpecDefault {

  def spec = suite("Direct Route Testing Examples")(
    suite("Simple handlers")(
      test("handler returns OK status") {
        val handler = Handler.ok.toRoutes
        val request = Request.get(URL(Path.root))

        for {
          response <- handler.runZIO(request)
        } yield assertTrue(response.status == Status.Ok)
      },
      test("handler returns text response") {
        val handler = Handler.text("Hello, World!").toRoutes
        val request = Request.get(URL(Path.root))

        for {
          response <- handler.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "Hello, World!")
      },
    ),
    suite("Routing and path matching")(
      test("specific route matches") {
        val routes = Routes(
          Method.GET / "hello" -> Handler.text("Hello!")
        )

        val request = Request.get(URL(Path.root / "hello"))

        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "Hello!")
      },
      test("fallback route handles unmatched paths") {
        val routes = Routes(
          Method.GET / "specific" -> Handler.text("Specific"),
          Method.GET / trailing -> Handler.text("Fallback"),
        )

        val request = Request.get(URL(Path.root / "any" / "path"))

        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "Fallback")
      },
    ),
    suite("Parameterized routes")(
      test("extract integer path parameter") {
        val routes = Routes(
          Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
            Response.text(s"User ID: $id")
          }
        )

        val request = Request.get(URL(Path.root / "users" / "42"))

        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "User ID: 42")
      },
      test("extract string path parameter") {
        val routes = Routes(
          Method.GET / "posts" / string("slug") -> handler { (slug: String, _: Request) =>
            Response.text(s"Post: $slug")
          }
        )

        val request = Request.get(URL(Path.root / "posts" / "my-post"))

        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "Post: my-post")
      },
    ),
    suite("Different HTTP methods")(
      test("GET request") {
        val routes = Routes(
          Method.GET / "items" -> Handler.text("GET response")
        )

        val request = Request.get(URL(Path.root / "items"))
        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "GET response")
      },
      test("POST request") {
        val routes = Routes(
          Method.POST / "items" -> Handler.text("POST response")
        )

        val request = Request.post(URL(Path.root / "items"), Body.empty)
        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "POST response")
      },
    ),
    suite("Request body handling")(
      test("handler can read request body") {
        val routes = Routes(
          Method.POST / "echo" -> handler { (req: Request) =>
            req.body.asString
              .map { body =>
                Response.text(s"Echo: $body")
              }
              .catchAll { _ =>
                ZIO.succeed(Response.status(Status.InternalServerError))
              }
          }
        )

        val request = Request.post(URL(Path.root / "echo"), Body.fromString("Hello"))
        for {
          response <- routes.runZIO(request)
          body <- response.body.asString
        } yield assertTrue(body == "Echo: Hello")
      },
    ),
  )
}
