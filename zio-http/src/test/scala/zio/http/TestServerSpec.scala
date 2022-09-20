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
      response <- // Http.fromFunctionZIO[Request] { params =>
        Client.request(
          Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        ).provideSome[Scope](Client.default)


      _ <- ZIO.debug("Response: " + response)
      // not connected to our server at all.
      // Consult HttpRunnableSpec
//      _ <- response(Request()).provideSome[Scope](Client.default)

      finalRequests <- TestServer.requests.debug

    } yield assertTrue(originalRequests.length == 0) && assertTrue(finalRequests.length == 1)
  }

}
