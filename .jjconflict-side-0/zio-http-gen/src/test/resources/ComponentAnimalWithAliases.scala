package test.component

import zio.Chunk
import zio.schema._

case class Animal(
  species: Species.Type,
  name: Name.Type,
  relatives: Chunk[Species.Type],
  age: Age.Type,
  id: Id.Type,
)
object Animal {
  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
}
