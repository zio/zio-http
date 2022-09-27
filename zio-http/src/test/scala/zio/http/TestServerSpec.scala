package zio.http

import zio._
import zio.http.URL.Location
import zio.http.model.Status.NotFound
import zio.http.model._
import zio.http.netty.server.NettyDriver
import zio.test._

object TestServerSpec extends ZIOSpecDefault{
  def spec = suite("TestServerSpec")(
  test("basic no state"){
    for {
      port <- ZIO.serviceWith[Server](_.port)
      testRequest = Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
      initialResponse <-
        Client.request(
          testRequest
        )
      _ <- TestServer.addHandler[Unit] {
                case _: Request  => Response(Status.Ok)
      }
      finalResponse <-
        Client.request(
          testRequest
        )

    } yield assertTrue(initialResponse.status == NotFound) && assertTrue(finalResponse.status == Status.Ok)
  }.provideSome[Scope with Client with Driver](
    ZLayer.fromZIO(TestServer.make),
  ),
    test("with state"){
      for {
        port <- ZIO.serviceWith[Server](_.port)
        testRequest = Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        _ <- TestServer.addHandlerState[Int] {
          case (state, _: Request)  =>
            if (state > 1)
              (state + 1, Response(Status.InternalServerError))
            else
              (state + 1, Response(Status.Ok))
        }

        response1 <-
          Client.request(
            testRequest
          )
        response2 <-
          Client.request(
            testRequest
          )
        response3 <-
          Client.request(
            testRequest
          )

      } yield assertTrue(response1.status == Status.Ok) &&
      assertTrue(response2.status == Status.Ok) &&
      assertTrue(response3.status == Status.InternalServerError)
    }.provideSome[Scope with Client with Driver](
      ZLayer.fromZIO(TestServer.make(0)),
    ),
    test("Exact Request=>Response version"){
      for {
        port <- ZIO.serviceWith[Server](_.port)
        testRequest = Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
        initialResponse <-
          Client.request(
            testRequest
          )
        _ <- TestServer.addHandlerExact[Unit](testRequest, Response(Status.Ok))
        finalResponse <-
          Client.request(
            testRequest
          )

      } yield assertTrue(initialResponse.status == NotFound) && assertTrue(finalResponse.status == Status.Ok)
    }.provideSome[Scope with Client with Driver](
      ZLayer.fromZIO(TestServer.make),
    ),
  ).provideSome[Scope](
      ServerConfig.liveOnOpenPort,
      Client.default,
      NettyDriver.default,
    )

}
