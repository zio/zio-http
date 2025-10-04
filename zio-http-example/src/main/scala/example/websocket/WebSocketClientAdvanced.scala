package example.websocket
import scala.annotation.nowarn

import zio._

import zio.http.ChannelEvent.Read
import zio.http._

object WebSocketSimpleClientAdvanced extends ZIOAppDefault {

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

  override val run = {
    ZIO.scoped(webSocketHandler) *>
      Console.readLine.flatMap(sendChatMessage).forever.forkDaemon *>
      ZIO.never
  }.provide(
    Client.default,
    ZLayer(Queue.bounded[String](100)),
  )

}
