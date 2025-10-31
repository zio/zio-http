package example.auth.digest.core

sealed abstract class QualityOfProtection(val name: String) {
  override def toString: String = name
}

object QualityOfProtection {
  case object Auth    extends QualityOfProtection("auth")
  case object AuthInt extends QualityOfProtection("auth-int")

  private val values: Set[QualityOfProtection] = Set(Auth, AuthInt)

  def fromString(s: String): Option[QualityOfProtection] =
    values.find(_.name.equalsIgnoreCase(s.trim))

  def fromChallenge(s: String): Set[QualityOfProtection] =
    s.split(",")
      .map(_.trim)
      .flatMap(QualityOfProtection.fromString)
      .toSet

  def fromChallenge(s: Option[String]): Set[QualityOfProtection] =
    s.fold(Set.empty[QualityOfProtection])(fromChallenge)
}
