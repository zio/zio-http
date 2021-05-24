package zhttp.core

trait Flip[A <: Direction, B <: Direction]
object Flip {
  implicit object in  extends Flip[Direction.In, Direction.Out]
  implicit object out extends Flip[Direction.Out, Direction.In]
}
