package test.component

import zio.schema._

case class Lion(
  eats: Animal.Zebra,
  enemy: Option[Animal.Alligator],
)
object Lion {
  implicit val codec: Schema[Lion] = DeriveSchema.gen[Lion]
}