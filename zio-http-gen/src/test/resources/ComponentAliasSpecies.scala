package test.component

import zio.prelude.Newtype

object Species extends Newtype[String] {

  implicit val codec: Schema[Species] = derive[Species]

}