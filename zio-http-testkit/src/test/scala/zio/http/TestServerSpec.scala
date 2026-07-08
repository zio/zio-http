package zio.http

import zio.blocks.endpoint.RoutePattern
import zio.test._

object TestServerSpec extends ZIOHttpSpec {

  private def request: Request =
    Request.get(url = URL.root).addHeader(Header.CacheControl.NoCache)

  def spec = suite("TestServerSpec")(
    test("with state") {
      val server = TestServer.make()
      var state  = 0
      server.addRoute(
        Route(
          RoutePattern.any,
          Handler.fromRequest { (_: Request) =>
            val current = state
            state += 1
            if (current > 0) Response(Status.InternalServerError) else Response(Status.Ok)
          },
        ),
      )

      val response1 = server.client.send(request)
      val response2 = server.client.send(request)
      assertTrue(response1.status == Status.Ok, response2.status == Status.InternalServerError)
    },
    suite("Exact Request=>Response version")(
      test("matches") {
        val server = TestServer.make()
        server.addRequestResponse(request, Response(Status.Ok))
        assertTrue(server.client.send(request).status == Status.Ok)
      },
      test("matches, ignoring additional headers") {
        val server = TestServer.make()
        server.addRequestResponse(request, Response(Status.Ok))
        val response = server.client.send(request.addHeader(Header.ContentLanguage("fr")))
        assertTrue(response.status == Status.Ok)
      },
      test("does not match different path") {
        val server = TestServer.make()
        server.addRequestResponse(request, Response(Status.Ok))
        val response = server.client.send(request.copy(url = request.url.path(Path.root / "unhandled")))
        assertTrue(response.status == Status.NotFound)
      },
      test("does not match different headers") {
        val server = TestServer.make()
        server.addRequestResponse(request, Response(Status.Ok))
        val response = server.client.send(request.copy(headers = Headers.empty.add(Header.CacheControl.Public)))
        assertTrue(response.status == Status.NotFound)
      },
    ),
    test("add routes to the server") {
      val server = TestServer.make()
      server.addRoutes(
        Routes(
          Route(RoutePattern.any, Handler.fromRequest((_: Request) => Response.text("fallback"))),
          Route(
            RoutePattern(Method.GET, Path("/hello/world")),
            Handler.fromRequest((_: Request) => Response.text("Hey there!")),
          ),
        ),
      )
      val helloResponse    = server.client.send(Request.get(URL.root / "hello" / "world"))
      val fallbackResponse = server.client.send(Request.get(URL.root / "any"))
      assertTrue(
        helloResponse.body.asString() == "Hey there!",
        fallbackResponse.body.asString() == "fallback",
      )
    },
  )
}
