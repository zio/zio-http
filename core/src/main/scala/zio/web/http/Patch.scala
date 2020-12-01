package zio.web.http

import zio.web.http.model.StatusCode

sealed trait Patch { self =>
  def +(that: Patch): Patch = Patch.Combine(self, that)
}

object Patch {
  private[zio] case object Empty                                   extends Patch
  final private[zio] case class AddHeaders(headers: HttpHeaders)   extends Patch
  final private[zio] case class SetStatus(status: StatusCode)      extends Patch
  final private[zio] case class Combine(left: Patch, right: Patch) extends Patch

  val empty: Patch                            = Empty
  def addHeaders(headers: HttpHeaders): Patch = AddHeaders(headers)
  def setStatus(status: StatusCode): Patch    = SetStatus(status)
}
