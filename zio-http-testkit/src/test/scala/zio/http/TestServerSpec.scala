package zio.http

import zio._
import zio.test._

import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver

object TestServerSpec extends ZIOHttpSpec {
  def status(response: Response): Status = response.status

  def spec = suite("TestServerSpec")(
    test("with state") {
      for {
        client      <- ZIO.service[Client]
        state       <- Ref.make(0)
        testRequest <- requestToCorrectPort
        _           <- TestServer.addRoute {
          Method.ANY / trailing -> handler {
            for {
              curState <- state.getAndUpdate(_ + 1)
            } yield {
              if (curState > 0)
                Response(Status.InternalServerError)
              else
                Response(Status.Ok)
            }
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
      } yield assertTrue(status(response1) == Status.Ok) &&
        assertTrue(status(response2) == Status.InternalServerError)
    }.provideSome[Client with Driver](
      TestServer.layer,
      Scope.default,
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

        } yield assertTrue(status(finalResponse) == Status.Ok)
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

        } yield assertTrue(status(finalResponse) == Status.Ok)
      },
      test("does not match different path") {
        for {
          client        <- ZIO.service[Client]
          testRequest   <- requestToCorrectPort
          _             <- TestServer.addRequestResponse(testRequest, Response(Status.Ok))
          finalResponse <-
            client(
              testRequest.copy(url = testRequest.url.path(Path.root / "unhandled")),
            )
        } yield assertTrue(status(finalResponse) == Status.NotFound)
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
        } yield assertTrue(status(finalResponse) == Status.NotFound)
      },
    )
      .provideSome[Client with Driver](
        TestServer.layer,
        Scope.default,
      ),
    test("add routes to the server") {
      for {
        client           <- ZIO.service[Client]
        testRequest      <- requestToCorrectPort
        _                <- TestServer.addRoutes {
          Routes(
            Method.GET / trailing          -> handler { Response.text("fallback") },
            Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
          )
        }
        helloResponse    <- client(Request.get(testRequest.url / "hello" / "world"))
        helloBody        <- helloResponse.body.asString
        fallbackResponse <- client(Request.get(testRequest.url / "any"))
        fallbackBody     <- fallbackResponse.body.asString
      } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
    }.provideSome[Client with Driver](
      TestServer.layer,
      Scope.default,
    ),
  ).provide(
    ZLayer.succeed(Server.Config.default.onAnyOpenPort),
    Client.default,
    NettyDriver.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )

  private def requestToCorrectPort =
    for {
      port <- ZIO.serviceWith[Server](_.port)
    } yield Request
      .get(url = URL.root.port(port))
      .addHeaders(Headers(Header.Accept(MediaType.text.`plain`)))

}
