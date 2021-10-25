package zhttp.http.middleware

import zhttp.http.{Header, Status}

sealed trait ResponsePatch { self =>
  def ++(that: ResponsePatch): ResponsePatch = ResponsePatch.Combine(self, that)
}

object ResponsePatch {
  case object Empty                                                   extends ResponsePatch
  final case class AddHeaders(headers: List[Header])                  extends ResponsePatch
  final case class RemoveHeaders(headers: List[String])               extends ResponsePatch
  final case class SetStatus(status: Status)                          extends ResponsePatch
  final case class Combine(left: ResponsePatch, right: ResponsePatch) extends ResponsePatch

  val empty: ResponsePatch                                = Empty
  def addHeaders(headers: List[Header]): ResponsePatch    = AddHeaders(headers)
  def removeHeaders(headers: List[String]): ResponsePatch = RemoveHeaders(headers)
  def setStatus(status: Status): ResponsePatch            = SetStatus(status)
}
