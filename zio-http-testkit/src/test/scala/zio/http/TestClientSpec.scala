package zio.http

import zio.blocks.endpoint.RoutePattern
import zio.http.ResultType._
import zio.test._

object TestClientSpec extends ZIOHttpSpec {

  def spec =
    suite("TestClient")(
      suite("addRequestResponse")(
        test("New behavior does not overwrite old") {
          val client   = TestClient.make()
          val request  = Request.get(URL.root)
          val request2 = Request.get(URL.root.path(Path("/users")))

          client.addRequestResponse(request, Response.ok)
          val goodResponse  = client.send(request)
          val badResponse   = client.send(request2)
          client.addRequestResponse(request2, Response.ok)
          val goodResponse2 = client.send(request)
          val badResponse2  = client.send(request2)

          assertTrue(
            goodResponse.status == Status.Ok,
            badResponse.status == Status.NotFound,
            goodResponse2.status == Status.Ok,
            badResponse2.status == Status.Ok,
          )
        },
      ),
      suite("addRoute")(
        test("all") {
          val client = TestClient.make()
          client.addRoute(Route(RoutePattern.any, Handler.fromRequest((_: Request) => Response.ok)))
          assertTrue(client.send(Request.get(URL.root)).status == Status.Ok)
        },
        test("partial") {
          val client = TestClient.make()
          client.addRoute(Route(RoutePattern.any(Method.GET), Handler.fromRequest((_: Request) => Response.ok)))
          assertTrue(client.send(Request.get(URL.root)).status == Status.Ok)
        },
        test("addRoute advanced") {
          val client       = TestClient.make()
          var requestCount = 0
          client.addRoute(
            Route(
              RoutePattern.any,
              Handler.fromRequest { (_: Request) =>
                requestCount += 1
                Response.ok
              },
            ),
          )
          val response     = client.send(Request.get(URL.root))
          assertTrue(response.status == Status.Ok, requestCount == 1)
        },
      ),
      test("addRoutes") {
        val client           = TestClient.make()
        client.addRoutes(
          Routes(
            Route(RoutePattern.any, Handler.fromRequest((_: Request) => Response.text("fallback"))),
            Route(
              RoutePattern(Method.GET, Path("/hello/world")),
              Handler.fromRequest((_: Request) => Response.text("Hey there!")),
            ),
          ),
        )
        val helloResponse    = client.send(Request.get(URL.root / "hello" / "world"))
        val fallbackResponse = client.send(Request.get(URL.root / "any"))
        assertTrue(
          helloResponse.body.asString() == "Hey there!",
          fallbackResponse.body.asString() == "fallback",
        )
      },
      test("setFallbackHandler") {
        val client         = TestClient.make()
        val failedRequests = new java.util.concurrent.ConcurrentLinkedQueue[Request]()
        client.setFallbackHandler { req => failedRequests.add(req); Response.notFound }
        client.addRoute(
          Route(RoutePattern(Method.GET, Path("/test")), Handler.fromRequest((_: Request) => Response.text("ok"))),
        )

        val successResponse1 = client.send(Request.get(URL.root / "test")).body.asString()
        val failResponse1    = client.send(Request.get(URL.root / "foo"))
        val successResponse2 = client.send(Request.get(URL.root / "test")).body.asString()
        val failResponse2    = client.send(Request.post(URL.root / "xyzzy", Body.empty))

        import scala.jdk.CollectionConverters._
        val failed = failedRequests.asScala.toList.map(req => (req.method, req.url))

        assertTrue(
          successResponse1 == "ok",
          successResponse2 == "ok",
          failResponse1 == Response.notFound,
          failResponse2 == Response.notFound,
          failed == List((Method.GET, URL.root / "foo"), (Method.POST, URL.root / "xyzzy")),
        )
      },
      suite("sad paths")(
        test("error when submitting a request to a blank TestClient") {
          val client = TestClient.make()
          assertTrue(client.send(Request.get(URL.root)).status == Status.NotFound)
        },
      ),
    )
}
