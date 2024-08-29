package zio.http.gen.model

import zio.schema.{DeriveSchema, Schema}

final case class BigModel(
  f1: Boolean,
  f2: Boolean,
  f3: Boolean,
  f4: Boolean,
  f5: Boolean,
  f6: Boolean,
  f7: Boolean,
  f8: Boolean,
  f9: Boolean,
  f10: Boolean,
  f11: Boolean,
  f12: Boolean,
  f13: Boolean,
  f14: Boolean,
  f15: Boolean,
  f16: Boolean,
  f17: Boolean,
  f18: Boolean,
  f19: Boolean,
  f20: Boolean,
  f21: Boolean,
  f22: Boolean,
  f23: Boolean,
)
object BigModel {
  implicit val codec: Schema[BigModel] = DeriveSchema.gen[BigModel]
}
