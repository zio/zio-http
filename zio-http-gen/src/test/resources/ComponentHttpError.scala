package test.component

import zio.schema._

case class HttpError(
  messages: Option[String],
)
object HttpError {

  implicit val codec: Schema[HttpError] = DeriveSchema.gen[HttpError]

}
