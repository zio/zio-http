package test.component

import zio.prelude.Newtype
import zio.schema.Schema

object Name extends Newtype[String] {
  implicit val schema: Schema[Name.Type] = Schema.primitive[String].transform(wrap, unwrap)
}
