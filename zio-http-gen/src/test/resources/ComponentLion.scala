package test.component

import zio.schema._

case class Lion(
  eats: Animal.Zebra,
)
object Lion {

  implicit val codec: Schema[Lion] = DeriveSchema.gen[Lion]

}