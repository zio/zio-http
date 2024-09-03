package test.component

import zio.Chunk
import zio.schema._

case class Animal(
  species: String,
  name: String,
  relatives: Chunk[String],
  age: Int,
  id: Int,
)
object Animal {
  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
}