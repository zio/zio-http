package zio.http

import zio._
import zio.test._

import zio.http.URL.Location
//import zio.http._
import zio.http.model._
//import zio.http.socket.SocketApp
//import zio.test.ZIOSpecDefault


object TestServerSpec extends ZIOSpec[TestServer]{
  val bootstrap = ZLayer.fromZIO(TestServer.make)
  def spec = test("use our test server"){
    for {
      originalRequests <- TestServer.requests
      _ <- ZIO.serviceWith[Server](_.install(Http.ok))
      port <- ZIO.serviceWith[Server](_.port)
      _ <- TestServer.feedRequests(
        Request()
      )
      response = Http.fromFunctionZIO[Request] { params =>
        Client.request(
          params
            .copy(url = URL(params.url.path, Location.Absolute(Scheme.HTTP, "localhost", port))),
        )
      }

      // not connected to our server at all.
      // Consult HttpRunnableSpec
      _ <- response(Request()).provideSome[Scope](Client.default)

      finalRequests <- TestServer.requests

    } yield assertTrue(originalRequests.length == 0) && assertTrue(finalRequests.length == 1)
  }

}
