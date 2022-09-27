package zio.http

import zio._
import zio.http.URL.Location
import zio.http.model._
import zio.test._

object TestServerOldSpec extends ZIOSpec[TestServerOld] {
  val bootstrap = TestServerOld.make
  def spec      = test("use our test server") {
    for {
      originalRequests <- TestServerOld.interactions
      _                <- ZIO.serviceWithZIO[Server](_.install(Http.ok))
      port             <- ZIO.serviceWith[Server](_.port)
      _                <-
        Client.request(
          Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port))),
        )
      _                <-
        Client.request(
          Request(url = URL(Path.root / "users", Location.Absolute(Scheme.HTTP, "localhost", port))),
        )
      finalRequests    <- TestServerOld.interactions

    } yield assertTrue(originalRequests.length == 0) && assertTrue(finalRequests.length == 2)
  }.provideSome[Scope with TestServerOld](ZLayer.succeed(ClientConfig()) >>> Client.default)

}
