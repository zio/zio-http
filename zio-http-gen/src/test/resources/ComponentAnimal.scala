package test.component

import zio.schema._
import zio.schema.annotation._

@noDiscriminator
sealed trait Animal
object Animal {

  implicit val codec: Schema[Animal] = DeriveSchema.gen[Animal]
  case class Alligator(
    @zio.schema.annotation.validate[Int](zio.schema.validation.Validation.greaterThan(-1)) age: Int,
    @zio.schema.annotation.validate[Float](zio.schema.validation.Validation.greaterThan(-1.0)) weight: Float,
    @zio.schema.annotation.validate[Int](zio.schema.validation.Validation.greaterThan(-1)) num_teeth: Int,
  ) extends Animal
  object Alligator {
    implicit val codec: Schema[Alligator] = DeriveSchema.gen[Alligator]
  }
  case class Zebra(
    @zio.schema.annotation.validate[Int](zio.schema.validation.Validation.greaterThan(-1)) age: Int,
    @zio.schema.annotation.validate[Float](zio.schema.validation.Validation.greaterThan(-1.0)) weight: Float,
    @zio.schema.annotation.validate[Int](zio.schema.validation.Validation.greaterThan(-1)) num_stripes: Int,
  ) extends Animal
  object Zebra {
    implicit val codec: Schema[Zebra] = DeriveSchema.gen[Zebra]
  }
}
