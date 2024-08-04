package test.component

import zio.schema._

case class Animal(
  id: Int,
  name: String,
  species: String,
  age: Int,
)
object Animal {

  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]

}