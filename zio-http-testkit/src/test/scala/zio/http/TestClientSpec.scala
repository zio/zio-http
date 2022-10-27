package zio.http

import zio._
import zio.http.model.{Method, Status}
import zio.http.ChannelEvent.{ChannelRead, UserEvent, UserEventTriggered}
import zio.http.socket.{SocketApp, SocketDecoder, SocketProtocol, WebSocketChannel, WebSocketChannelEvent, WebSocketFrame}
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
            // TODO Use state or something
            _               <- TestClient.addHandler(request => ZIO.succeed(Response.ok))
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.Ok),
        ),
      ),
      suite("sad paths")(
        test("error when submitting a request to a blank TestServer")(
          for {
            response <- Client.request(Request.get(URL.root))
          } yield assertTrue(response.status == Status.NotFound),
        )
      ),
      suite("socket ops")(
        /**
         * Scenario:
         *    TestClient.addSocketApp(app)
         *      app will be standing in for live server behavior
         *    Client.socket(app)
         *    Apps should send messages back and forth until a predetermined end
         */
        test("happy path") {
          val messageFilter: Http[Any, Nothing, WebSocketChannelEvent, (Channel[WebSocketFrame], String)] =
            Http.collect[WebSocketChannelEvent] { case ChannelEvent(channel, ChannelRead(WebSocketFrame.Text(message))) =>
              (channel, message)
            }

          val messageSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
            Http.collectZIO[(WebSocketChannel, String)] {
              case (ch, text) if text.contains("Hi Client") =>
                  ch.writeAndFlush(WebSocketFrame.text("Hi Server"), await = true).debug("Client got message: " + text)
            }

          val channelSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] = Http.empty

          val messageSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] = messageFilter >>>
            Http.collectZIO[(WebSocketChannel, String)] {
              case (ch, text) if text.contains("Hi Server") =>
                ZIO.debug("Server got message: " + text) *> ch.close()
            }

          val channelSocketServer
          : Http[Any, Throwable, WebSocketChannelEvent, Unit] =
            Http.collectZIO[WebSocketChannelEvent] {
              case ChannelEvent(ch, UserEventTriggered(UserEvent.HandshakeComplete))  =>
                ch.writeAndFlush(WebSocketFrame.text("Hi Client"))

              case ChannelEvent(_, ChannelRead(WebSocketFrame.Close(status, reason))) =>
                Console.printLine("Closing channel with status: " + status + " and reason: " + reason)
            }

          val httpSocketClient: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
            messageSocketClient ++ channelSocketClient

          val httpSocketServer: Http[Any, Throwable, WebSocketChannelEvent, Unit] =
            messageSocketServer ++ channelSocketServer

          val protocol = SocketProtocol.default.withSubProtocol(Some("json")) // Setup protocol settings

          val decoder = SocketDecoder.default.withExtensions(allowed = true) // Setup decoder settings

          val socketAppClient: SocketApp[Any] = // Combine all channel handlers together
            httpSocketClient.toSocketApp
              .withDecoder(decoder)   // Setup websocket decoder config
              .withProtocol(protocol) // Setup websocket protocol config

          val socketAppServer: SocketApp[Any] = // Combine all channel handlers together
            httpSocketServer.toSocketApp
              .withDecoder(decoder)   // Setup websocket decoder config
              .withProtocol(protocol) // Setup websocket protocol config
          for {
            _ <- TestClient.addSocketApp(socketAppServer)
            client <- ZIO.serviceWithZIO[Client](_.socket(pathSuffix = "")(socketAppClient))
//            _ <- client.
          } yield assertCompletes
        }
      )
    ).provide(TestClient.layer, Scope.default)

}


/*
    Server
      - SocketOpened
        - write("Hi client")

      - MessageReceived("Hi Server")
        - close(channel)

    Client
      - MessageReceived
        - write("Hi Server")

 */