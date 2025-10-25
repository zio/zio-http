package zio.http

import zio._
import zio.test._

import zio.http.ChannelEvent.Read

object TestClientSpec extends ZIOHttpSpec {

  def spec =
    suite("TestClient")(
      suite("addRequestResponse")(
        test("New behavior does not overwrite old") {
          val request  = Request.get(URL.root)
          val request2 = Request.get(URL(Path.decode("/users")))
          for {
            client        <- ZIO.service[Client]
            _             <- TestClient.addRequestResponse(request, Response.ok)
            goodResponse  <- client(request)
            badResponse   <- client(request2)
            _             <- TestClient.addRequestResponse(request2, Response.ok)
            goodResponse2 <- client(request)
            badResponse2  <- client(request2)
          } yield assertTrue(
            goodResponse.status == Status.Ok,
            badResponse.status == Status.NotFound,
            goodResponse2.status == Status.Ok,
            badResponse2.status == Status.Ok,
          )
        },
      ),
      suite("addHandler")(
        test("all")(
          for {
            client   <- ZIO.service[Client]
            _        <- TestClient.addRoute { Method.ANY / trailing -> handler(Response.ok) }
            response <- client(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
        test("partial")(
          for {
            client   <- ZIO.service[Client]
            _        <- TestClient.addRoute { Method.GET / trailing -> handler(Response.ok) }
            response <- client(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
        test("addHandler advanced")(
          for {
            client       <- ZIO.service[Client]
            requestCount <- Ref.make(0)
            _ <- TestClient.addRoute { Method.ANY / trailing -> handler(requestCount.update(_ + 1).as(Response.ok)) }
            response   <- client(Request.get(URL.root))
            finalCount <- requestCount.get
          } yield assertTrue(response.status == Status.Ok, finalCount == 1),
        ),
      ),
      test("addRoutes") {
        for {
          client           <- ZIO.service[Client]
          _                <- TestClient.addRoutes {
            Routes(
              Method.GET / trailing          -> handler { Response.text("fallback") },
              Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
            )
          }
          helloResponse    <- client(Request.get(URL.root / "hello" / "world"))
          helloBody        <- helloResponse.body.asString
          fallbackResponse <- client(Request.get(URL.root / "any"))
          fallbackBody     <- fallbackResponse.body.asString
        } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
      },
      test("setFallbackHandler") {
        for {
          client <- ZIO.service[Client]
          ref    <- Ref.Synchronized.make[List[Request]](Nil)
          _      <- TestClient.setFallbackHandler(req => ref.update(_ :+ req).as(Response.notFound))
          _      <- TestClient.addRoute(
            Method.GET / "test" -> handler { Response.text("ok") },
          )

          successResponse1 <- client(Request.get(URL.root / "test")).flatMap(_.body.asString)
          failResponse1    <- client(Request.get(URL.root / "foo"))
          successResponse2 <- client(Request.get(URL.root / "test")).flatMap(_.body.asString)
          failResponse2    <- client(Request.post(URL.root / "xyzzy", Body.empty))
          failedRequests   <- ref.get.map(_.map(req => (req.method, req.url)))
        } yield assertTrue(
          successResponse1 == "ok",
          successResponse2 == "ok",
          failResponse1 == Response.notFound,
          failResponse2 == Response.notFound,
          failedRequests == List((Method.GET, URL.root / "foo"), (Method.POST, URL.root / "xyzzy")),
        )

      },
      suite("sad paths")(
        test("error when submitting a request to a blank TestServer")(
          for {
            client   <- ZIO.service[Client]
            response <- client(Request.get(URL.root))
          } yield assertTrue(response.status == Status.NotFound),
        ),
      ),
      suite("socket ops")(
        test("happy path") {
          val socketClient: WebSocketApp[Any] =
            Handler.webSocket { channel =>
              channel.receiveAll {
                case ChannelEvent.Read(WebSocketFrame.Text("Hi Client")) =>
                  channel.send(Read(WebSocketFrame.text("Hi Server")))

                case _ =>
                  ZIO.unit
              }
            }

          val socketServer: WebSocketApp[Any] =
            Handler.webSocket { channel =>
              channel.receiveAll {
                case ChannelEvent.Read(WebSocketFrame.Text("Hi Server")) =>
                  channel.send(Read(WebSocketFrame.text("Hi Client")))

                case _ => ZIO.unit
              }
            }

          for {
            _        <- TestClient.installSocketApp(socketServer)
            response <- ZIO.serviceWithZIO[Client](_.socket(socketClient))
          } yield assertTrue(response.status == Status.SwitchingProtocols)
        },
      ),
    ).provide(TestClient.layer, Scope.default)

}
