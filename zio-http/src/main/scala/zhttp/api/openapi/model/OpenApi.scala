package zhttp.api.openapi.model

import zio.json.{DeriveJsonEncoder, JsonEncoder}

case class OpenApi(
  openapi: String = "3.0.0",
  info: Info,
  paths: Paths,
)

object OpenApi {
  implicit val enc: JsonEncoder[OpenApi] = DeriveJsonEncoder.gen
}

final case class Info(
  version: String,
  title: String,
  description: String,
)

object Info {

  implicit val enc: JsonEncoder[Info] = DeriveJsonEncoder.gen
}
