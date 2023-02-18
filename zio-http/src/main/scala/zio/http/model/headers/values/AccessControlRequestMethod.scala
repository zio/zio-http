package zio.http.model.headers.values

import scala.util.Try

import zio.http.model.Method

sealed trait AccessControlRequestMethod

object AccessControlRequestMethod {
  final case class RequestMethod(method: Method) extends AccessControlRequestMethod
  case object InvalidMethod                      extends AccessControlRequestMethod

  def toAccessControlRequestMethod(value: String): AccessControlRequestMethod = Try {
    val method = Method.fromString(value)
    if (method == Method.CUSTOM(value)) InvalidMethod
    else RequestMethod(method)
  }.getOrElse(InvalidMethod)

  def fromAccessControlRequestMethod(requestMethod: AccessControlRequestMethod): String = requestMethod match {
    case RequestMethod(method) => method.text
    case InvalidMethod         => ""
  }
}
