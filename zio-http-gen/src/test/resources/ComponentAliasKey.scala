package test.component

import zio.prelude.Newtype
import zio.schema.Schema
import java.util.UUID

object Key extends Newtype[UUID] {
  implicit val schema: Schema[Key.Type] = Schema.primitive[UUID].transform(wrap, unwrap)
}
