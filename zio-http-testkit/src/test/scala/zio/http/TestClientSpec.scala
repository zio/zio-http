package zio.http

import zio._
import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.model.{Method, Status}
import zio.http.socket._
import zio.test._

object TestClientSpec extends ZIOSpecDefault {
  def spec =
    suite("TestClient")(
      suite("addRequestResponse")(
        test("New behavior does not overwrite old") {
          val request  = Request.get(URL.root)
          val request2 = Request.get(URL(Path.decode("users")))
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
          val messageUnwrapper: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
            Http.collect[WebSocketChannelEvent] {
              case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
                (channel, message)
            }

          val greetingToClient                                                       = "Hi Client"
          val messageSocketClient: HttpSocket = messageUnwrapper >>>
            Http.collectZIO[(WebSocketChannel, String)] { case (ch, `greetingToClient`) =>
              ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true)
            }

          val channelSocketClient: HttpSocket =
            Http.empty

          val messageSocketServer: HttpSocket = messageUnwrapper >>>
            Http.collectZIO[(WebSocketChannel, String)] { case (ch, "Hi Server") =>
              ch.close()
            }

          val channelSocketServer: HttpSocket =
            Http.collectZIO[WebSocketChannelEvent] {
              case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete)) =>
                ch.writeAndFlush(WebSocketFrame.text(greetingToClient))
            }

          val httpSocketClient: HttpSocket =
            messageSocketClient ++ channelSocketClient

          val httpSocketServer: HttpSocket =
            messageSocketServer ++ channelSocketServer

          for {
            _        <- TestClient.addSocketApp(httpSocketServer.toSocketApp)
            response <- ZIO.serviceWithZIO[Client](_.socket(pathSuffix = "")(httpSocketClient.toSocketApp))
          } yield assertTrue(response.status == Status.SwitchingProtocols)
        },
      ),
    ).provide(TestClient.layer, Scope.default)

}
