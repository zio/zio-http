package zio.http.model.headers.values

sealed trait AccessControlAllowCredentials

object AccessControlAllowCredentials {

  /**
   * The Access-Control-Allow-Credentials header is sent in response to a
   * preflight request which includes the Access-Control-Request-Headers to
   * indicate whether or not the actual request can be made using credentials.
   */
  case object AllowCredentials extends AccessControlAllowCredentials

  /**
   * The Access-Control-Allow-Credentials header is not sent in response to a
   * preflight request.
   */
  case object DoNotAllowCredentials extends AccessControlAllowCredentials

  def fromAccessControlAllowCredentials(
    accessControlAllowCredentials: AccessControlAllowCredentials,
  ): String =
    accessControlAllowCredentials match {
      case AllowCredentials      => "true"
      case DoNotAllowCredentials => "false"
    }

  def toAccessControlAllowCredentials(value: String): AccessControlAllowCredentials =
    value match {
      case "true"  => AllowCredentials
      case "false" => DoNotAllowCredentials
      case _       => DoNotAllowCredentials
    }

  def toAccessControlAllowCredentials(value: Boolean): AccessControlAllowCredentials =
    value match {
      case true  => AllowCredentials
      case false => DoNotAllowCredentials
    }
}
