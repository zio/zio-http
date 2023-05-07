package zio.http

import zio._
import zio.test._

import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.http.{Method, Status, WebSocketFrame}

object TestClientSpec extends ZIOSpecDefault {
  def spec =
    suite("TestClient")(
      suite("addRequestResponse")(
        test("New behavior does not overwrite old") {
          val request  = Request.get(URL.root)
          val request2 = Request.get(URL(Path.decode("/users")))
          for {
            _             <- TestClient.addRequestResponse(request, Response.ok)
            goodResponse  <- Client.request(request)
            badResponse   <- Client.request(request2)
            _             <- TestClient.addRequestResponse(request2, Response.ok)
            goodResponse2 <- Client.request(request)
            badResponse2  <- Client.request(request2)
          } yield assertTrue(goodResponse.status == Status.Ok) && assertTrue(badResponse.status == Status.NotFound) &&
            assertTrue(goodResponse2.status == Status.Ok) && assertTrue(badResponse2.status == Status.Ok)
        },
      ),
      suite("addHandler")(
        test("all")(
          for {
            _        <- TestClient.addHandler { case _ => ZIO.succeed(Response.ok) }
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
        test("partial")(
          for {
            _ <- TestClient.addHandler { case request if request.method == Method.GET => ZIO.succeed(Response.ok) }
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
        test("addHandler advanced")(
          for {
            requestCount <- Ref.make(0)
            _            <- TestClient.addHandler { case _ => requestCount.update(_ + 1) *> ZIO.succeed(Response.ok) }
            response     <- Client.request(Request.get(URL.root))
            finalCount   <- requestCount.get
          } yield assertTrue(response.status == Status.Ok) && assertTrue(finalCount == 1),
        ),
      ),
      suite("sad paths")(
        test("error when submitting a request to a blank TestServer")(
          for {
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.NotFound),
        ),
      ),
      suite("socket ops")(
        test("happy path") {
          val socketClient: Http[Any, Throwable, WebSocketChannel, Unit] =
            Http.webSocket { channel =>
              channel.receiveAll {
                case ChannelEvent.Read(WebSocketFrame.Text("Hi Client")) =>
                  channel.send(Read(WebSocketFrame.text("Hi Server")))

                case _ =>
                  ZIO.unit
              }
            }

          val socketServer: Http[Any, Throwable, WebSocketChannel, Unit] =
            Http.webSocket { channel =>
              channel.receiveAll {
                case ChannelEvent.Read(WebSocketFrame.Text("Hi Server")) =>
                  channel.send(Read(WebSocketFrame.text("Hi Client")))

                case _ => ZIO.unit
              }
            }

          for {
            _        <- TestClient.installSocketApp(socketServer)
            response <- ZIO.serviceWithZIO[Client](_.socket(pathSuffix = "")(socketClient.toSocketApp))
          } yield assertTrue(response.status == Status.SwitchingProtocols)
        },
      ),
    ).provide(TestClient.layer, Scope.default)

}
