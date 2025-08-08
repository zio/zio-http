package example.auth.digest.core

sealed abstract class QualityOfProtection(val name: String) {
  override def toString: String = name
}

object QualityOfProtection {
  case object Auth     extends QualityOfProtection("auth")
  case object AuthInt  extends QualityOfProtection("auth-int")

  val values: List[QualityOfProtection] = List(Auth, AuthInt)

  def fromString(s: String): Option[QualityOfProtection] =
    values.find(_.name.equalsIgnoreCase(s.trim))
}
