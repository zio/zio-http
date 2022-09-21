package zio.http

import zio._
import zio.test._

import zio.http.URL.Location
//import zio.http._
import zio.http.model._
//import zio.http.socket.SocketApp
//import zio.test.ZIOSpecDefault


object TestServerSpec extends ZIOSpec[TestServer]{
  val bootstrap = TestServer.make
  def spec = test("use our test server"){
    for {
      originalRequests <- TestServer.requests
      _ <- ZIO.serviceWithZIO[Server](_.install(Http.ok))
      port <- ZIO.serviceWith[Server](_.port)
//      _ <- TestServer.feedRequests(
//        Request()
//      )
      response <-
        Client.request(
          Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        )
      _ <- // Http.fromFunctionZIO[Request] { params =>
        Client.request(
          Request(url = URL(Path.root / "users", Location.Absolute(Scheme.HTTP, "localhost", port)))
        )
      _ <- ZIO.debug("Response: " + response)
      finalRequests <- TestServer.requests.debug

    } yield assertTrue(originalRequests.length == 0) && assertTrue(finalRequests.length == 2)
  }.provideSome[Scope with TestServer](ZLayer.succeed(ClientConfig()) >>> Client.default)

}
