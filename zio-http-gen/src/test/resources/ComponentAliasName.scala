package test.component

import zio.prelude.Newtype

object Name extends Newtype[String] {

  implicit val codec: Schema[Name] = derive[Name]

}