package zhttp.endpoint

import zhttp.endpoint.Parameter.Literal
import zhttp.http.Path

import scala.annotation.tailrec

/**
 * A special type-safe data structure that holds a list of Endpoint Parameters.
 */
sealed trait ParameterList[+A] { self =>
  private[zhttp] def extract(path: Path): Option[A] = ParameterList.extract(self, path.toList.reverse)

  def ::[A1 >: A, B, C](head: Parameter[B])(implicit ev: CanCombine.Aux[A1, B, C]): ParameterList[C] =
    ParameterList.Cons(head, self)

  def ::(literal: String): ParameterList[A] = ParameterList.Cons(Literal(literal), self)
}
object ParameterList           {
  private[zhttp] case object Empty                                                          extends ParameterList[Unit]
  private[zhttp] final case class Cons[A, B, C](head: Parameter[B], tail: ParameterList[A]) extends ParameterList[C]

  def empty: ParameterList[Unit] = Empty

  private def extract[A](r: ParameterList[A], p: List[String]): Option[A] = {
    @tailrec
    def loop(r: ParameterList[Any], p: List[String], output: List[Any]): Option[Any] = {
      r match {
        case Empty            => TupleBuilder(output)
        case Cons(head, tail) =>
          if (p.isEmpty) None
          else {
            head.parse(p.head) match {
              case Some(value) =>
                if (value.isInstanceOf[Unit]) loop(tail, p.tail, output) else loop(tail, p.tail, value :: output)
              case None        => None
            }
          }
      }
    }
    loop(r.asInstanceOf[ParameterList[Any]], p, List.empty[Any]).asInstanceOf[Option[A]]
  }
}
