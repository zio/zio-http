package test.component

import zio.schema._
import zio.schema.annotation._
import zio.schema.annotation.validate
import zio.schema.validation.Validation

@noDiscriminator
sealed trait Animal
object Animal {

  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
  case class Alligator(
    @validate[Int](Validation.greaterThan(-1)) age: Int,
    @validate[Float](Validation.greaterThan(-1.0)) weight: Float,
    @validate[Int](Validation.greaterThan(-1)) num_teeth: Int,
  ) extends Animal
  object Alligator {
    implicit val codec: Schema[Alligator] = DeriveSchema.gen[Alligator]
  }
  case class Zebra(
    @validate[Int](Validation.greaterThan(-1)) age: Int,
    @validate[Float](Validation.greaterThan(-1.0)) weight: Float,
    @validate[Int](Validation.greaterThan(-1)) num_stripes: Int,
  ) extends Animal
  object Zebra {
    implicit val codec: Schema[Zebra] = DeriveSchema.gen[Zebra]
  }
}
