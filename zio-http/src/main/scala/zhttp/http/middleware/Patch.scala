package zhttp.http.middleware

import zhttp.http.{Header, Response, Status}

/**
 * Models the set of operations that one would want to apply on a Response.
 */
sealed trait Patch { self =>
  def ++(that: Patch): Patch                           = Patch.Combine(self, that)
  def apply[R, E](res: Response[R, E]): Response[R, E] = ???
}

object Patch {
  case object Empty                                     extends Patch
  final case class AddHeaders(headers: List[Header])    extends Patch
  final case class RemoveHeaders(headers: List[String]) extends Patch
  final case class SetStatus(status: Status)            extends Patch
  final case class Combine(left: Patch, right: Patch)   extends Patch

  val empty: Patch                                = Empty
  def addHeaders(headers: List[Header]): Patch    = AddHeaders(headers)
  def removeHeaders(headers: List[String]): Patch = RemoveHeaders(headers)
  def setStatus(status: Status): Patch            = SetStatus(status)
}
