package example.testing

import zio._
import zio.http._
import zio.test._

object TestServerSequentialRequests extends ZIOSpecDefault {
  def spec = test("multiple requests share state") {
    for {
      client <- ZIO.service[Client]
      port <- ZIO.serviceWithZIO[Server](_.port)
      counter <- Ref.make(0)

      _ <- TestServer.addRoute {
        Method.GET / "count" -> handler { (_: Request) =>
          counter.updateAndGet(_ + 1).map(n => Response.text(s"$n"))
        }
      }

      // First request
      resp1 <- client(Request.get(URL.root.port(port) / "count"))
      body1 <- resp1.body.asString

      // Second request—counter has incremented
      resp2 <- client(Request.get(URL.root.port(port) / "count"))
      body2 <- resp2.body.asString
    } yield assertTrue(
      body1 == "1",
      body2 == "2"
    )
  }.provide(TestServer.default, Client.default, Scope.default)
}
