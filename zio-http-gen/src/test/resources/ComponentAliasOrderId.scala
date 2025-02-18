package test.component

import zio.prelude.Newtype
import zio.schema.Schema
import java.util.UUID

object OrderId extends Newtype[UUID] {
  implicit val schema: Schema[OrderId.Type] = Schema.primitive[UUID].transform(wrap, unwrap)
}
