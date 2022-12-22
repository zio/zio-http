//package zio.http.model.headers.values
//
//sealed trait AuthenticationScheme {
//  val name: String
//}
//
//object AuthenticationScheme {
//
//  case object Basic extends AuthenticationScheme {
//    override val name: String = "Basic"
//  }
//
//  case object Bearer extends AuthenticationScheme {
//    override val name: String = "Bearer"
//  }
//
//  case object Digest extends AuthenticationScheme {
//    override val name: String = "Digest"
//  }
//
//  case object HOBA extends AuthenticationScheme {
//    override val name: String = "HOBA"
//  }
//
//  case object Mutual extends AuthenticationScheme {
//    override val name: String = "Mutual"
//  }
//
//  case object Negotiate extends AuthenticationScheme {
//    override val name: String = "Negotiate"
//  }
//
//  case object OAuth extends AuthenticationScheme {
//    override val name: String = "OAuth"
//  }
//
//  case object Scram     extends AuthenticationScheme {
//    override val name: String = "SCRAM"
//  }
//  case object ScramSha1 extends AuthenticationScheme {
//    override val name: String = "SCRAM-SHA-1"
//  }
//
//  case object ScramSha256 extends AuthenticationScheme {
//    override val name: String = "SCRAM-SHA-256"
//  }
//
//  case object Vapid extends AuthenticationScheme {
//    override val name: String = "vapid"
//  }
//
//  case object `AWS4-HMAC-SHA256` extends AuthenticationScheme {
//    override val name: String = "AWS4-HMAC-SHA256"
//  }
//
//  case object Invalid extends AuthenticationScheme {
//    override val name: String = ""
//  }
//
//  def fromAuthenticationScheme(authenticationScheme: AuthenticationScheme): String =
//    authenticationScheme.name
//
//  def toAuthenticationScheme(name: String): AuthenticationScheme = {
//    name.trim.toUpperCase match {
//      case "BASIC"            => Basic
//      case "BEARER"           => Bearer
//      case "DIGEST"           => Digest
//      case "HOBA"             => HOBA
//      case "MUTUAL"           => Mutual
//      case "NEGOTIATE"        => Negotiate
//      case "OAUTH"            => OAuth
//      case "SCRAM"            => Scram
//      case "SCRAM-SHA-1"      => ScramSha1
//      case "SCRAM-SHA-256"    => ScramSha256
//      case "VAPID"            => Vapid
//      case "AWS4-HMAC-SHA256" => `AWS4-HMAC-SHA256`
//      case _                  => Invalid
//    }
//  }
//
//}
