package example.testing

import zio._
import zio.http._
import zio.test._

object TestClientFallbackHandler extends ZIOSpecDefault {
  def spec = test("fallback captures unexpected requests") {
    for {
      client <- ZIO.service[Client]
      unexpectedCalls <- Ref.make[List[Request]](Nil)

      // Capture any unexpected requests
      _ <- TestClient.setFallbackHandler { req =>
        unexpectedCalls.update(_ :+ req).as(Response.notFound)
      }

      // Configure expected route
      _ <- TestClient.addRoute {
        Method.GET / "expected" -> handler(Response.ok)
      }

      // Make expected request
      _ <- client(Request.get(URL.root / "expected"))
      // Make unexpected request
      _ <- client(Request.get(URL.root / "unexpected"))

      // Verify what was captured
      calls <- unexpectedCalls.get
    } yield assertTrue(
      calls.length == 1,
      calls.head.url.path.encode == "/unexpected"
    )
  }.provide(TestClient.layer, Scope.default)
}
