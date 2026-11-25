package test.component

import zio.schema._
import zio.schema.annotation._

@noDiscriminator
sealed trait Animal {
  def age: Age.Type
  def weight: Weight.Type
}
object Animal       {

  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
  case class Alligator(
    age: Age.Type,
    weight: Weight.Type,
    num_teeth: Int,
  ) extends Animal
  object Alligator {
    implicit val codec: Schema[Alligator] = DeriveSchema.gen[Alligator]
  }
  case class Zebra(
    age: Age.Type,
    weight: Weight.Type,
    num_stripes: Int,
  ) extends Animal
  object Zebra {
    implicit val codec: Schema[Zebra] = DeriveSchema.gen[Zebra]
  }
}
