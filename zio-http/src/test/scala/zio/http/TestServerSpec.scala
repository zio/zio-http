package zio.http

import zio._
import zio.test._

import zio.http.URL.Location
import zio.http.model._

object TestServerSpec extends ZIOSpec[TestServer]{
  val bootstrap = TestServer.make
  def spec = test("use our test server"){
    for {
      originalRequests <- TestServer.interactions
      _ <- ZIO.serviceWithZIO[Server](_.install(Http.ok))
      port <- ZIO.serviceWith[Server](_.port)
      _ <-
        Client.request(
          Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        )
      _ <-
        Client.request(
          Request(url = URL(Path.root / "users", Location.Absolute(Scheme.HTTP, "localhost", port)))
        )
      finalRequests <- TestServer.interactions

    } yield assertTrue(originalRequests.length == 0) && assertTrue(finalRequests.length == 2)
  }.provideSome[Scope with TestServer](ZLayer.succeed(ClientConfig()) >>> Client.default)

}
