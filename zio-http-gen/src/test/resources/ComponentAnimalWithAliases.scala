package test.component

import zio.Chunk
import zio.schema._

case class Animal(
  age: Age.Type,
  id: Id.Type,
  name: Name.Type,
  relatives: Chunk[Species.Type],
  species: Species.Type,
)
object Animal {
  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
}