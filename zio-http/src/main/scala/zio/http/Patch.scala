package zio.http

import zio.http.model._

import scala.annotation.tailrec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Models the set of operations that one would want to apply on a Response.
 */
sealed trait Patch { self =>
  def ++(that: Patch): Patch         = Patch.Combine(self, that)
  def apply(res: Response): Response = {

    @tailrec
    def loop(res: Response, patch: Patch): Response =
      patch match {
        case Patch.Empty                  => res
        case Patch.AddHeaders(headers)    => res.addHeaders(headers)
        case Patch.RemoveHeaders(headers) => res.removeHeaders(headers)
        case Patch.SetStatus(status)      => res.setStatus(status)
        case Patch.Combine(self, other)   => loop(self(res), other)
        case Patch.UpdateHeaders(f)       => res.updateHeaders(f)
      }

    loop(res, self)
  }
}

object Patch {
  case object Empty                                     extends Patch
  final case class AddHeaders(headers: Headers)         extends Patch
  final case class RemoveHeaders(headers: List[String]) extends Patch
  final case class SetStatus(status: Status)            extends Patch
  final case class Combine(left: Patch, right: Patch)   extends Patch
  final case class UpdateHeaders(f: Headers => Headers) extends Patch

  def empty: Patch                                              = Empty
  def addHeader(header: Header): Patch                          = AddHeaders(header)
  def addHeader(headers: Headers): Patch                        = AddHeaders(headers)
  def addHeader(name: CharSequence, value: CharSequence): Patch = AddHeaders(Headers(name, value))
  def removeHeaders(headers: List[String]): Patch               = RemoveHeaders(headers)
  def setStatus(status: Status): Patch                          = SetStatus(status)
  def updateHeaders(f: Headers => Headers): Patch               = UpdateHeaders(f)
}
