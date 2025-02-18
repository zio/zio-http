package test.component

import zio.prelude.Newtype
import zio.schema.Schema

object Id extends Newtype[Int] {
  implicit val schema: Schema[Id.Type] = Schema.primitive[Int].transform(wrap, unwrap)
}
