package test.component

import zio.Chunk
import zio.schema._

case class Animal(
  age: Int,
  id: Int,
  name: String,
  relatives: Chunk[String],
  species: String,
)
object Animal {
  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
}