package test.component

import zio.schema._

case class Animal(
  id: Id.Type,
  name: Name.Type,
  species: Species.Type,
  age: Age.Type,
)
object Animal {

  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]

}