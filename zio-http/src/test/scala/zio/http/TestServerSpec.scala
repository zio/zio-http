package zio.http

import zio._
import zio.http.URL.Location
import zio.http.model.Status.NotFound
import zio.http.model._
import zio.http.netty.server.NettyDriver
import zio.test._

object TestServerSpec extends ZIOSpecDefault{

  def spec = suite("TestServerSpec")(
    test("stateless"){
      for {
        testRequest <- requestToCorrectPort
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
            if (state > 0)
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
      } yield assertTrue(response1.status == Status.Ok) &&
        assertTrue(response2.status == Status.InternalServerError)
    }.provideSome[Scope with Client with Driver](
      ZLayer.fromZIO(TestServer.make(0)),
    ),
    suite("Exact Request=>Response version") (
      test("matches") {
        for {
          testRequest <- requestToCorrectPort
          _ <- TestServer.addHandlerExact[Unit](testRequest, Response(Status.Ok))
          finalResponse <-
            Client.request(
              testRequest
            )

        } yield assertTrue(finalResponse.status == Status.Ok)
      },
      test("matches, ignoring additional headers") {
        for {
          testRequest <- requestToCorrectPort
          _ <- TestServer.addHandlerExact[Unit](testRequest, Response(Status.Ok))
          finalResponse <-
            Client.request(
              testRequest.addHeaders(Headers.contentLanguage("French"))
            )

        } yield assertTrue(finalResponse.status == Status.Ok)
      },
      test("does not match different path") {
        for {
          testRequest <- requestToCorrectPort
          _ <- TestServer.addHandlerExact[Unit](testRequest, Response(Status.Ok))
          finalResponse <-
            Client.request(
              testRequest.copy(url = testRequest.url.setPath(Path.root/"unhandled"))
            )
        } yield assertTrue(finalResponse.status == Status.InternalServerError)
      },
      test("does not match different headers") {
        for {
          testRequest <- requestToCorrectPort
          _ <- TestServer.addHandlerExact[Unit](testRequest, Response(Status.Ok))
          finalResponse <-
            Client.request(
              testRequest.copy(headers = Headers.cacheControl("cache"))
            )
        } yield assertTrue(finalResponse.status == Status.InternalServerError)
      }
    )
    .provideSome[Scope with Client with Driver](
        ZLayer.fromZIO(TestServer.make),
      ),
  ).provideSome[Scope](
    Network.live,
    ServerConfig.liveOnOpenPort,
    Client.default,
    NettyDriver.default,
  )

  private def requestToCorrectPort =
    for {
      port <- ZIO.serviceWith[Server](_.port)
    } yield Request(url = URL(Path.root, Location.Absolute(Scheme.HTTP, "localhost", port)))
      .addHeaders(Headers.accept("text"))

}
