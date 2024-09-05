package test.component

import zio.prelude.Newtype
import zio.schema.Schema

object Weight extends Newtype[Float] {
  implicit val schema: Schema[Weight.Type] = Schema.primitive[Float].transform(wrap, unwrap)
}
