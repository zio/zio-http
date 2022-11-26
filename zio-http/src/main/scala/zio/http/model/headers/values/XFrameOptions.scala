package zio.http.model.headers.values

sealed trait XFrameOptions

object XFrameOptions {

  case object Deny       extends XFrameOptions
  case object SameOrigin extends XFrameOptions
  case object Invalid    extends XFrameOptions

  def toXFrameOptions(value: String): XFrameOptions = {
    value.trim.toUpperCase match {
      case "DENY"       => Deny
      case "SAMEORIGIN" => SameOrigin
      case _            => Invalid
    }
  }

  def fromXFrameOptions(xFrameOptions: XFrameOptions): String =
    xFrameOptions match {
      case Deny       => "DENY"
      case SameOrigin => "SAMEORIGIN"
      case Invalid    => ""
    }

}
