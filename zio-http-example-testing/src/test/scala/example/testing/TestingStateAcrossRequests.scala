package example.testing

import zio._
import zio.http._
import zio.test._

object TestingStateAcrossRequests extends ZIOSpecDefault {
  def spec = test("counter persists across requests") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)
      counter <- Ref.make(0)

      _ <- TestServer.addRoute {
        Method.GET / "increment" -> handler { (_: Request) =>
          counter.updateAndGet(_ + 1).map(n => Response.text(s"Count: $n"))
        }
      }

      // Multiple requests share the same counter
      resp1 <- client(Request.get(URL.root.port(port) / "increment"))
      body1 <- resp1.body.asString
      resp2 <- client(Request.get(URL.root.port(port) / "increment"))
      body2 <- resp2.body.asString
    } yield assertTrue(
      body1 == "Count: 1",
      body2 == "Count: 2"
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
