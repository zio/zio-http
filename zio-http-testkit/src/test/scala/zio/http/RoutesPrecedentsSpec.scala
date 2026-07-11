package zio.http

import zio.blocks.endpoint.RoutePattern
import zio.http.ResultType._
import zio.test.TestAspect.shrinks
import zio.test._

/**
 * Verifies that when the same route pattern is registered multiple times on a
 * [[TestServer]], the most-recently-added registration takes precedence.
 *
 * This intentionally exercises plain [[Route]]/[[Handler]] registration instead
 * of `zio.http.endpoint.Endpoint` — the endpoint module's `implement` API is
 * out of scope for the testkit and is exercised by its own test suite.
 */
object RoutesPrecedentsSpec extends ZIOHttpSpec {

  def spec: Spec[Any, Nothing] =
    test("last registered route wins") {
      // Re-registering the same route pattern on the same TestServer must make each
      // newer handler take precedence over every previously-registered one.
      val server = TestServer.make()
      check(Gen.fromIterable(List(1, 2, 3, 4, 5))) { code =>
        server.addRoute(
          Route(
            RoutePattern(Method.POST, Path("/api")),
            Handler.fromRequest((_: Request) => Response.text(code.toString)),
          ),
        )
        val response = server.client.send(Request.post(URL.root / "api", Body.fromString(""""this is some input"""")))
        assertTrue(response.body.asString() == code.toString)
      }
    } @@ shrinks(0)
}
