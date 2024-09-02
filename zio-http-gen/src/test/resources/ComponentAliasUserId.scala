package test.component

import zio.prelude.Newtype
import zio.schema.Schema
import java.util.UUID

object UserId extends Newtype[UUID] {
  implicit val schema: Schema[UserId.Type] = Schema.primitive[UUID].transform(wrap, unwrap)
}