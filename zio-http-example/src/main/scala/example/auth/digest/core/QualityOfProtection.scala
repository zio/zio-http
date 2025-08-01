package example.auth.digest.core

sealed trait QualityOfProtection {
  def name: String
}

object QualityOfProtection {
  case object Auth extends QualityOfProtection {
    val name = "auth"
  }

  case object AuthInt extends QualityOfProtection {
    val name = "auth-int"
  }

  def fromString(s: String): Option[QualityOfProtection] = s.toLowerCase match {
    case "auth"     => Some(Auth)
    case "auth-int" => Some(AuthInt)
    case _          => None
  }
}
