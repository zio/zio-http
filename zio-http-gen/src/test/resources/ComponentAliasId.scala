package test.component

import zio.prelude.Newtype

object Id extends Newtype[Int] {

  implicit val codec: Schema[Id] = derive[Id]

}