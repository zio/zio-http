package zio.http.model.headers.values

import java.net._

sealed trait ContentBase

object ContentBase {
  final case class BaseUri(uri: URI) extends ContentBase
  case object InvalidContentBase     extends ContentBase

  def fromContentBase(cb: ContentBase): String = cb match {
    case BaseUri(uri)       => uri.toString
    case InvalidContentBase => ""
  }

  def toContentBase(s: CharSequence): ContentBase =
    try {
      BaseUri(new URL(s.toString).toURI)
    } catch {
      case _: Throwable => InvalidContentBase
    }

  def uri(uri: URI): ContentBase = BaseUri(uri)
}
