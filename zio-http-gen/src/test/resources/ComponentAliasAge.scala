package test.component

import zio.prelude.Newtype

object Age extends Newtype[Int] {

  implicit val codec: Schema[Age] = derive[Age]

}