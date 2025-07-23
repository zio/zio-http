package example.auth.digest.core

import example.auth.digest.core.HashAlgorithm._
import zio._

import java.security.MessageDigest

trait HashService {
  def hash(data: String, algorithm: HashAlgorithm): UIO[String]
  def keyedHash(data: String, algorithm: HashAlgorithm, secret: String): UIO[String]
}

object HashService {
  object HashServiceLive extends HashService {

    def hash(data: String, algorithm: HashAlgorithm): UIO[String] =
      ZIO.succeed {
        val md = algorithm match {
          case MD5 | MD5_SESS =>
            MessageDigest.getInstance("MD5")
          case SHA256 | SHA256_SESS =>
            MessageDigest.getInstance("SHA-256")
          case SHA512 | SHA512_SESS                           =>
            MessageDigest.getInstance("SHA-512")
        }
        md.digest(data.getBytes("UTF-8"))
          .map(b => String.format("%02x", b & 0xff))
          .mkString
      }

    def keyedHash(data: String, algorithm: HashAlgorithm, secret: String): UIO[String] =
      hash(s"$secret:$data", algorithm)
  }

  val live: ULayer[HashService] = ZLayer.succeed(HashServiceLive)
}
