package example.websocket
import zio._
import zio.http.ChannelEvent.Read
import zio.http._

import scala.annotation.nowarn

object WebSocketSimpleClient extends ZIOAppDefault {

  def sendChatMessage(message: String): ZIO[Queue[String], Throwable, Unit] =
    ZIO.serviceWithZIO[Queue[String]](_.offer(message).unit)

  def processQueue(channel: WebSocketChannel): ZIO[Queue[String], Throwable, Unit] = {
    for {
      queue <- ZIO.service[Queue[String]]
      msg   <- queue.take
      _     <- channel.send(Read(WebSocketFrame.Text(msg)))
    } yield ()
  }.forever.forkDaemon.unit

  private def webSocketHandler: ZIO[Queue[String] with Client with Scope, Throwable, Response] =
    Handler.webSocket { channel =>
      for {
        _ <- processQueue(channel)
        _ <- channel.receiveAll {
          case Read(WebSocketFrame.Text(text)) =>
            Console.printLine(s"Server: $text")
          case _                               =>
            ZIO.unit
        }
      } yield ()
    }.connect("ws://localhost:8080/subscriptions")

  @nowarn("msg=dead code")
  override val run =
    ZIO
      .scoped(for {
        _ <- webSocketHandler
        _ <- Console.readLine.flatMap(sendChatMessage).forever.forkDaemon
        _ <- ZIO.never
      } yield ())
      .provide(
        Client.default,
        ZLayer(Queue.bounded[String](100)),
      )

}
