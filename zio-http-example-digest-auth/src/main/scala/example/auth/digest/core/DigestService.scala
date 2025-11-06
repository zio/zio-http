package example.auth.digest.core

import example.auth.digest.core.DigestAlgorithm._
import zio.Config.Secret
import zio._
import zio.http._

import java.net.URI
import java.security.MessageDigest

trait DigestService {
  def computeResponse(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    nc: NC,
    cnonce: String,
    algorithm: DigestAlgorithm,
    qop: QualityOfProtection,
    uri: URI,
    method: Method,
    body: Option[String] = None,
  ): UIO[String]
}

object DigestService {
  val live: ZLayer[Any, Nothing, DigestService] =
    ZLayer.succeed(DigestServiceLive)
}

object DigestServiceLive extends DigestService {
  def computeResponse(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    nc: NC,
    cnonce: String,
    algorithm: DigestAlgorithm,
    qop: QualityOfProtection,
    uri: URI,
    method: Method,
    body: Option[String] = None,
  ): UIO[String] = {
    for {
      a1       <- computeA1(username, realm, password, nonce, cnonce, algorithm)
      ha1      <- hash(a1, algorithm)
      a2       <- computeA2(method, uri, algorithm, qop, body)
      ha2      <- hash(a2, algorithm)
      response <- computeFinalResponse(ha1, ha2, nonce, nc, cnonce, qop, algorithm)
    } yield response
  }

  // Private helper methods
  private def computeA1(
    username: String,
    realm: String,
    password: Secret,
    nonce: String,
    cnonce: String,
    algorithm: DigestAlgorithm,
  ): UIO[String] = {
    val baseA1 = s"$username:$realm:${password.stringValue}"

    algorithm match {
      case DigestAlgorithm.MD5_SESS | DigestAlgorithm.SHA256_SESS | DigestAlgorithm.SHA512_SESS =>
        hash(baseA1, algorithm)
          .map(ha1 => s"$ha1:$nonce:$cnonce")
      case _                                                                                    =>
        ZIO.succeed(baseA1)
    }
  }

  private def computeA2(
    method: Method,
    uri: URI,
    algorithm: DigestAlgorithm,
    qop: QualityOfProtection,
    entityBody: Option[String],
  ): UIO[String] = {
    qop match {
      case QualityOfProtection.AuthInt =>
        entityBody match {
          case Some(body) =>
            hash(body, algorithm)
              .map(hbody => s"${method.name}:${uri.getPath}:$hbody")
          case None       =>
            ZIO.succeed(s"${method.name}:${uri.getPath}:")
        }
      case _                           =>
        ZIO.succeed(s"${method.name}:${uri.getPath}")
    }
  }

  private def computeFinalResponse(
    ha1: String,
    ha2: String,
    nonce: String,
    nc: NC,
    cnonce: String,
    qop: QualityOfProtection,
    algorithm: DigestAlgorithm,
  ): UIO[String] =
    hash(s"$ha1:$nonce:$nc:$cnonce:$qop:$ha2", algorithm)

  private def hash(data: String, algorithm: DigestAlgorithm): UIO[String] =
    ZIO.succeed {
      val md = algorithm match {
        case MD5 | MD5_SESS       =>
          MessageDigest.getInstance("MD5")
        case SHA256 | SHA256_SESS =>
          MessageDigest.getInstance("SHA-256")
        case SHA512 | SHA512_SESS =>
          MessageDigest.getInstance("SHA-512")
      }
      md.digest(data.getBytes("UTF-8"))
        .map(b => f"${b & 0xff}%02x")
        .mkString
    }
}
