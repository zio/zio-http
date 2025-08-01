package example.auth.digest.core

sealed trait HashAlgorithm {
  def name: String
  def digestSize: Int
}

object HashAlgorithm {
  case object MD5 extends HashAlgorithm {
    val name       = "MD5"
    val digestSize = 128
  }

  case object MD5_SESS extends HashAlgorithm {
    val name       = "MD5-sess"
    val digestSize = 128
  }

  case object SHA256 extends HashAlgorithm {
    val name       = "SHA-256"
    val digestSize = 256
  }

  case object SHA256_SESS extends HashAlgorithm {
    val name       = "SHA-256-sess"
    val digestSize = 256
  }

  case object SHA512 extends HashAlgorithm {
    val name       = "SHA-512"
    val digestSize = 512
  }

  case object SHA512_SESS extends HashAlgorithm {
    val name       = "SHA-512-sess"
    val digestSize = 512
  }


  def fromString(s: String): Option[HashAlgorithm] = s.toLowerCase match {
    case "md5"          => Some(MD5)
    case "md5-sess"     => Some(MD5_SESS)
    case "sha-256"      => Some(SHA256)
    case "sha-256-sess" => Some(SHA256_SESS)
    case "sha-512"      => Some(SHA512)
    case "sha-512-sess" => Some(SHA512_SESS)
    case _              => None
  }
}
