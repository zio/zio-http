package test.component

import zio.json.ast.Json
import zio.schema._
import zio.schema.annotation.fieldName

case class Animal(
  name: String,
  eats: Json,
  @fieldName("extra_attributes") extraAttributes: Map[String, Json],
)
object Animal {
  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
}
