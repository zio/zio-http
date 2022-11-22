package zio.http.model.headers.values

import zio.http.URL

/**
 * The Access-Control-Allow-Origin response header indicates whether the
 * response can be shared with requesting code from the given origin.
 *
 * For requests without credentials, the literal value "*" can be specified as a
 * wildcard; the value tells browsers to allow requesting code from any origin
 * to access the resource. Attempting to use the wildcard with credentials
 * results in an error.
 *
 * <origin> Specifies an origin. Only a single origin can be specified. If the
 * server supports clients from multiple origins, it must return the origin for
 * the specific client making the request.
 *
 * null Specifies the origin "null".
 */
sealed trait AccessControlAllowOrigin {
  val origin: String
}

object AccessControlAllowOrigin {

  final case class ValidAccessControlAllowOrigin(value: String) extends AccessControlAllowOrigin {
    override val origin = value
  }

  case object InvalidAccessControlAllowOrigin extends AccessControlAllowOrigin {
    override val origin = ""
  }

  def fromAccessControlAllowOrigin(accessControlAllowOrigin: AccessControlAllowOrigin): String = {
    accessControlAllowOrigin.origin
  }

  def toAccessControlAllowOrigin(value: String): AccessControlAllowOrigin = {
    if (value == "null" || value == "*") {
      ValidAccessControlAllowOrigin(value)
    } else {
      URL.fromString(value) match {
        case Left(_)                                              => InvalidAccessControlAllowOrigin
        case Right(url) if url.host.isEmpty || url.scheme.isEmpty => InvalidAccessControlAllowOrigin
        case Right(_)                                             => ValidAccessControlAllowOrigin(value)
      }
    }

  }
}
