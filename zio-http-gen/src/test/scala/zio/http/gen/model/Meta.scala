package zio.http.gen.model

import zio.schema.annotation.{caseName, discriminatorName}
import zio.schema.{DeriveSchema, Schema}

@discriminatorName("type")
sealed trait Meta
object Meta {

  implicit val codec: Schema[Meta] = DeriveSchema.gen[Meta]

  @caseName("a")
  case class MetaA(
    t: String,
    i: Int,
  ) extends Meta
  object MetaA {
    implicit val codec: Schema[MetaA] = DeriveSchema.gen[MetaA]
  }

  @caseName("b")
  case class MetaB(
    t: String,
    i: Int,
  ) extends Meta
  object MetaB {
    implicit val codec: Schema[MetaB] = DeriveSchema.gen[MetaB]
  }
}
