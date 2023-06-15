package zio.http

import zio._
import zio.test._

import zio.http.netty.server.NettyDriver

object TestServerSpec extends ZIOSpecDefault {

  def spec = suite("TestServerSpec")(
    test("with state") {
      for {
        client      <- ZIO.service[Client]
        state       <- Ref.make(0)
        testRequest <- requestToCorrectPort
        _           <- TestServer.addHandler { case (_: Request) =>
          for {
            curState <- state.getAndUpdate(_ + 1)
          } yield {
            if (curState > 0)
              Response(Status.InternalServerError)
            else
              Response(Status.Ok)
          }
        }
        response1   <-
          client(
            testRequest,
          )
        response2   <-
          client(
            testRequest,
          )
      } yield assertTrue(response1.status == Status.Ok) &&
        assertTrue(response2.status == Status.InternalServerError)
    }.provideSome[Client with Driver with Scope](
      TestServer.layer,
    ),
    suite("Exact Request=>Response version")(
      test("matches") {
        for {
          client        <- ZIO.service[Client]
          testRequest   <- requestToCorrectPort
          _             <- TestServer.addRequestResponse(testRequest, Response(Status.Ok))
          finalResponse <-
            client(
              testRequest,
            )

        } yield assertTrue(finalResponse.status == Status.Ok)
      },
      test("matches, ignoring additional headers") {
        for {
          client        <- ZIO.service[Client]
          testRequest   <- requestToCorrectPort
          _             <- TestServer.addRequestResponse(testRequest, Response(Status.Ok))
          finalResponse <-
            client(
              testRequest.addHeaders(Headers(Header.ContentLanguage.French)),
            )

        } yield assertTrue(finalResponse.status == Status.Ok)
      },
      test("does not match different path") {
        for {
          client        <- ZIO.service[Client]
          testRequest   <- requestToCorrectPort
          _             <- TestServer.addRequestResponse(testRequest, Response(Status.Ok))
          finalResponse <-
            client(
              testRequest.copy(url = testRequest.url.withPath(Path.root / "unhandled")),
            )
        } yield assertTrue(finalResponse.status == Status.NotFound)
      },
      test("does not match different headers") {
        for {
          client        <- ZIO.service[Client]
          testRequest   <- requestToCorrectPort
          _             <- TestServer.addRequestResponse(testRequest, Response(Status.Ok))
          finalResponse <-
            client(
              testRequest.copy(headers = Headers(Header.CacheControl.Public)),
            )
        } yield assertTrue(finalResponse.status == Status.NotFound)
      },
    )
      .provideSome[Client with Driver with Scope](
        TestServer.layer,
      ),
  ).provide(
    ZLayer.succeed(Server.Config.default.onAnyOpenPort),
    Client.default,
    NettyDriver.live,
    Scope.default,
  )

  private def requestToCorrectPort =
    for {
      port <- ZIO.serviceWith[Server](_.port)
    } yield Request
      .get(url = URL.root.withPort(port))
      .addHeaders(Headers(Header.Accept(MediaType.text.`plain`)))

}
