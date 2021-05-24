package zhttp.core

sealed trait Direction
object Direction {
  case object In  extends Direction
  case object Out extends Direction

  type In  = In.type
  type Out = Out.type
}
