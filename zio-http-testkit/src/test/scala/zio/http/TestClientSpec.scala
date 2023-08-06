package zio.http

import zio._
import zio.test._

import zio.http.ChannelEvent.{Read, UserEvent, UserEventTriggered}
import zio.http.{Method, Status, WebSocketFrame}

object TestClientSpec extends ZIOSpecDefault {
  def extractStatus(response: Response): Status = response.status

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
          } yield assertTrue(extractStatus(goodResponse) == Status.Ok) && assertTrue(
            extractStatus(badResponse) == Status.NotFound,
          ) &&
            assertTrue(extractStatus(goodResponse2) == Status.Ok) && assertTrue(
              extractStatus(badResponse2) == Status.Ok,
            )
        },
      ),
      suite("addHandler")(
        test("all")(
          for {
            client   <- ZIO.service[Client]
            _        <- TestClient.addHandler { case _ => ZIO.succeed(Response.ok) }
            response <- client(Request.get(URL.root))
          } yield assertTrue(extractStatus(response) == Status.Ok),
        ),
        test("partial")(
          for {
            client <- ZIO.service[Client]
            _      <- TestClient.addHandler { case request if request.method == Method.GET => ZIO.succeed(Response.ok) }
            response <- client(Request.get(URL.root))
          } yield assertTrue(extractStatus(response) == Status.Ok),
        ),
        test("addHandler advanced")(
          for {
            client       <- ZIO.service[Client]
            requestCount <- Ref.make(0)
            _            <- TestClient.addHandler { case _ => requestCount.update(_ + 1) *> ZIO.succeed(Response.ok) }
            response     <- client(Request.get(URL.root))
            finalCount   <- requestCount.get
          } yield assertTrue(extractStatus(response) == Status.Ok) && assertTrue(finalCount == 1),
        ),
      ),
      suite("sad paths")(
        test("error when submitting a request to a blank TestServer")(
          for {
            client   <- ZIO.service[Client]
            response <- client(Request.get(URL.root))
          } yield assertTrue(extractStatus(response) == Status.NotFound),
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
          } yield assertTrue(extractStatus(response) == Status.SwitchingProtocols)
        },
      ),
    ).provideSome[Scope](TestClient.layer)

}
