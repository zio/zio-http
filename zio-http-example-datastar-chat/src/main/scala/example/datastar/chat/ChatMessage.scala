package example.datastar.chat

import zio.schema._

case class ChatMessage(
  id: String,
  username: String,
  content: String,
  timestamp: Long,
)

object ChatMessage {
  def apply(username: String, content: String): ChatMessage =
    ChatMessage(java.util.UUID.randomUUID().toString, username, content, System.currentTimeMillis())

  implicit val schema: Schema[ChatMessage] = DeriveSchema.gen[ChatMessage]
}
