package zhttp.http.middleware

import zhttp.http.{Header, Response, Status}

/**
 * Models the set of operations that one would want to apply on a Response.
 */
sealed trait Patch { self =>
  def ++(that: Patch): Patch = Patch.Combine(self, that)
  def apply[R, E](res: Response[R, E]): Response[R, E] = {
    val s = res.status
    val h = res.headers
    val d = res.data

    self match {
      case Patch.Empty                  => res
      case Patch.AddHeaders(headers)    => Response(s, headers ++ h, d)
      case Patch.RemoveHeaders(headers) =>
        val newHeaders = h.filter(p => headers.contains(p.name))
        Response(s, newHeaders, d)
      case Patch.SetStatus(status)      => Response(status, h, d)
      case Patch.Combine(left, right)   =>
        val res1 = left(res)
        val res2 = right(res1)
        res2
    }
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
