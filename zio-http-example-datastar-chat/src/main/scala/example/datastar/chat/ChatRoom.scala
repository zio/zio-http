package example.datastar.chat

import zio._
import zio.stream._

case class ChatRoom(
  messages: Ref[List[ChatMessage]],
  subscribers: Hub[ChatMessage],
)

object ChatRoom {
  def make: ZIO[Any, Nothing, ChatRoom] =
    for {
      messages <- Ref.make(List.empty[ChatMessage])
      hub      <- Hub.unbounded[ChatMessage]
    } yield ChatRoom(messages, hub)

  def addMessage(message: ChatMessage): ZIO[ChatRoom, Nothing, Unit] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      for {
        _ <- room.messages.update(_ :+ message)
        _ <- room.subscribers.publish(message)
      } yield ()
    }

  def getMessages: ZIO[ChatRoom, Nothing, List[ChatMessage]] =
    ZIO.serviceWithZIO[ChatRoom](_.messages.get)

  def subscribe: ZIO[ChatRoom & Scope, Nothing, UStream[ChatMessage]] =
    ZIO.serviceWithZIO[ChatRoom] { room =>
      room.subscribers.subscribe.map(ZStream.fromQueue(_))
    }

  val layer: ZLayer[Any, Nothing, ChatRoom] =
    ZLayer.fromZIO(make)
}
