package example.auth.digest.core

import zio._
import zio.http.Header
import example.auth.digest.core.DigestAlgorithm.MD5
import example.auth.digest.core.DigestAuthError.MissingRequiredField
import example.auth.digest.core.QualityOfProtection.Auth

case class DigestChallenge(
  realm: String,
  nonce: String,
  opaque: Option[String] = None,
  algorithm: DigestAlgorithm = MD5,
  qop: Set[QualityOfProtection] = Set(Auth),
  stale: Boolean = false,
  domain: Option[Set[String]] = None,
  charset: Option[String] = Some("UTF-8"),
  userhash: Boolean = false,
) {
  def toHeader: Header.WWWAuthenticate.Digest = {
    Header.WWWAuthenticate.Digest(
      realm = Some(realm),
      nonce = Some(nonce),
      opaque = opaque,
      algorithm = Some(algorithm.name),
      qop = Some(qop.map(_.name).mkString(", ")),
      stale = Some(stale),
      domain = domain.flatMap(_.headOption),
      charset = charset,
      userhash = Some(userhash),
    )
  }
}

object DigestChallenge {
  def fromHeader(header: Header.WWWAuthenticate.Digest): ZIO[Any, DigestAuthError, DigestChallenge] =
    for {
      realm <- ZIO
        .fromOption(header.realm)
        .orElseFail(MissingRequiredField("realm"))
      nonce <- ZIO
        .fromOption(header.nonce)
        .orElseFail(MissingRequiredField("nonce"))
    } yield DigestChallenge(
      realm = realm,
      nonce = nonce,
      opaque = header.opaque,
      algorithm = DigestAlgorithm.fromString(header.algorithm).getOrElse(MD5),
      qop = QualityOfProtection.fromChallenge(header.qop),
      stale = header.stale.getOrElse(false),
      domain = header.domain.map(Set(_)),
      charset = header.charset,
      userhash = header.userhash.getOrElse(false),
    )

}
