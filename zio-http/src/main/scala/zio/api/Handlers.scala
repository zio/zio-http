package zhttp.api

import scala.language.implicitConversions

case class Handlers[-R, +E](toList: List[Handler[R, E, _, _, _]]) {
  def ++[R1 <: R, E1 >: E](that: Handler[R1, E1, _, _, _]): Handlers[R1, E1] =
    Handlers(toList :+ that)

  def ++[R1 <: R, E1 >: E](that: Handlers[R1, E1]): Handlers[R1, E1] =
    Handlers(toList ++ that.toList)
}

object Handlers {

  def apply[R, E](handler: Handler[R, E, _, _, _]): Handlers[R, E] =
    Handlers(List(handler))

  implicit def handlerToHandlers[R, E](handler: Handler[R, E, _, _, _]): Handlers[R, E] =
    Handlers(List(handler))

}
