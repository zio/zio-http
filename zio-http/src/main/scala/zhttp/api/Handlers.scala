package zhttp.api

import scala.language.implicitConversions

sealed trait Handlers[-R, +E] { self =>
  def ++[R1 <: R, E1 >: E](that: Handler[R1, E1, _, _, _]): Handlers[R1, E1] =
    Handlers.Concat[R1, E1](self, Handlers.Single[R1, E1](that))

}

object Handlers {

  def handlersToList[R, E](handlers: Handlers[R, E]): List[Handler[R, E, _, _, _]] =
    handlers match {
      case Concat(l, r) => handlersToList(l) ++ handlersToList(r)
      case Single(h)    => List(h)
    }

  def apply[R, E](handler: Handler[R, E, _, _, _]): Handlers[R, E] =
    Single[R, E](handler)

  implicit def handlerToHandlers[R, E](handler: Handler[R, E, _, _, _]): Handlers[R, E] =
    Single[R, E](handler)

  final case class Single[R, E](handler: Handler[R, E, _, _, _]) extends Handlers[R, E]

  final case class Concat[R, E](left: Handlers[R, E], right: Handlers[R, E]) extends Handlers[R, E]
}
