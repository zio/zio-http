package zhttp.http.middleware

import zhttp.http.{Header, Response, Status}

import scala.annotation.tailrec

/**
 * Models the set of operations that one would want to apply on a Response.
 */
sealed trait Patch { self =>
  def ++(that: Patch): Patch = Patch.Combine(self, that)
  def apply[R, E](res: Response[R, E]): Response[R, E] = {

    @tailrec
    def loop[R1, E1](res: Response[R1, E1], patch: Patch): Response[R1, E1] =
      patch match {
        case Patch.Empty                  => res
        case Patch.AddHeaders(headers)    => res.addHeaders(headers)
        case Patch.RemoveHeaders(headers) => res.removeHeaders(headers)
        case Patch.SetStatus(status)      => res.setStatus(status)
        case Patch.Combine(self, other)   => loop[R1, E1](self(res), other)
      }

    loop(res, self)
  }
}

object Patch {
  case object Empty                                     extends Patch
  final case class AddHeaders(headers: List[Header])    extends Patch
  final case class RemoveHeaders(headers: List[String]) extends Patch
  final case class SetStatus(status: Status)            extends Patch
  final case class Combine(left: Patch, right: Patch)   extends Patch

  def empty: Patch                                = Empty
  def addHeaders(headers: List[Header]): Patch    = AddHeaders(headers)
  def removeHeaders(headers: List[String]): Patch = RemoveHeaders(headers)
  def setStatus(status: Status): Patch            = SetStatus(status)
}
