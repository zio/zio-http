package test.component

import zio.prelude.Newtype
import zio.schema.Schema

object Age extends Newtype[Int] {
  implicit val schema: Schema[Age.Type] = Schema.primitive[Int].transform(wrap, unwrap)
}
