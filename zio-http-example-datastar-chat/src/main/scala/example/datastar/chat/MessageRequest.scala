package example.datastar.chat

import zio.schema._

case class MessageRequest(username: String, message: String)

object MessageRequest {
  implicit val schema: Schema[MessageRequest] = DeriveSchema.gen[MessageRequest]
}
