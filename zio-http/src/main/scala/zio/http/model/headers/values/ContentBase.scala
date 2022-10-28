package zio.http.model.headers.values

import com.sun.jndi.toolkit.url.Uri

sealed trait ContentBase

object ContentBase {
  final case class BaseUri(uri: Uri) extends ContentBase
  case object InvalidContentBase     extends ContentBase

  def fromContentBase(cb: ContentBase): String = cb match {
    case BaseUri(uri)       => uri.toString
    case InvalidContentBase => ""
  }

  def toContentBase(s: String): ContentBase =
    try {
      BaseUri(new Uri(s))
    } catch {
      case _: Throwable => InvalidContentBase
    }

  def uri(uri: Uri): ContentBase = BaseUri(uri)
}
