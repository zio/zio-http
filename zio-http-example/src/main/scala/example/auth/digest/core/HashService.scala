package example.auth.digest.core

import zio._

import java.security.MessageDigest

trait HashService {
  def hash(data: String, algorithm: HashAlgorithm): Task[String]
  def keyedHash(data: String, algorithm: HashAlgorithm, secret: String): Task[String]
}

object HashService {
  object HashServiceLive extends HashService {

    def hash(data: String, algorithm: HashAlgorithm): Task[String] =
      ZIO.attempt {
        val md = algorithm match {
          case HashAlgorithm.MD5                                =>
            MessageDigest.getInstance("MD5")
          case HashAlgorithm.SHA256 | HashAlgorithm.SHA256_SESS =>
            MessageDigest.getInstance("SHA-256")
          case HashAlgorithm.SHA512                             =>
            MessageDigest.getInstance("SHA-512")
        }
        md.digest(data.getBytes("UTF-8"))
          .map(b => String.format("%02x", b & 0xff))
          .mkString
      }

    def keyedHash(data: String, algorithm: HashAlgorithm, secret: String): Task[String] =
      hash(s"$secret:$data", algorithm)
  }

  val live: ULayer[HashService] = ZLayer.succeed(HashServiceLive)
}
