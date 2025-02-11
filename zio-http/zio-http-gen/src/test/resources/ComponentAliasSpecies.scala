package test.component

import zio.prelude.Newtype
import zio.schema.Schema

object Species extends Newtype[String] {
  implicit val schema: Schema[Species.Type] = Schema.primitive[String].transform(wrap, unwrap)
}