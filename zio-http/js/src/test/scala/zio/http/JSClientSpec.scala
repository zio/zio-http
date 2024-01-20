package zio.http

import zio._
import zio.test._

object JSClientSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ClientSpec")(
      suite("HTTP")(
        test("Get") {
          for {
            response <- ZIO.serviceWithZIO[Client] { _.url(url"https://www.google.com").get("") }
            string   <- response.body.asString
          } yield assertTrue(response.status.isSuccess, string.startsWith("<!doctype html>"))
        },
      ),
//      suite("WebSocket")(
//        test("Echo") {
//          def sendChatMessage(message: String): ZIO[Queue[String], Throwable, Unit] =
//            ZIO.serviceWithZIO[Queue[String]](_.offer(message).unit)
//
//          def processQueue(channel: WebSocketChannel): ZIO[Queue[String], Throwable, Unit] = {
//            for {
//              queue <- ZIO.service[Queue[String]]
//              msg   <- queue.take
//              _     <- channel.send(Read(WebSocketFrame.Text(msg)))
//            } yield ()
//          }.forever.forkDaemon.unit
//
//          val webSocketHandler: ZIO[Queue[String] with Client with Scope, Throwable, Response] =
//            Handler.webSocket { channel =>
//              for {
//                _ <- sendChatMessage("Hello, World!")
//                _ <- processQueue(channel)
//                _ <- channel.receiveAll {
//                  case Read(WebSocketFrame.Text(text)) =>
//                    Console.printLine(s"Server: $text")
//                  case _                               =>
//                    ZIO.unit
//                }
//              } yield ()
//            }.connect("ws://localhost:8080/subscriptions")
//          for {
//            _     <- webSocketHandler
//            consoleMessages <- TestConsole.output
//          } yield assertTrue(consoleMessages.contains("Server: Hello, World!"))
//        }.provideSome[Scope & Client](ZLayer(Queue.bounded[String](100))),
//      ),
    ).provideSome[Scope](ZClient.default)
}
