package example.auth.digest.core

sealed abstract class DigestAlgorithm(val name: String, val digestSize: Int) {
  override def toString: String = name
}

object DigestAlgorithm {
  case object MD5         extends DigestAlgorithm("MD5", 128)
  case object MD5_SESS    extends DigestAlgorithm("MD5-sess", 128)
  case object SHA256      extends DigestAlgorithm("SHA-256", 256)
  case object SHA256_SESS extends DigestAlgorithm("SHA-256-sess", 256)
  case object SHA512      extends DigestAlgorithm("SHA-512", 512)
  case object SHA512_SESS extends DigestAlgorithm("SHA-512-sess", 512)

  val values: List[DigestAlgorithm] =
    List(MD5, MD5_SESS, SHA256, SHA256_SESS, SHA512, SHA512_SESS)

  def fromString(s: String): Option[DigestAlgorithm] =
    values.find(_.name.equalsIgnoreCase(s.trim))
}
