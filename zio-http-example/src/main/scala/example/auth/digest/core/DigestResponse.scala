package example.auth.digest.core

import java.net.URI

import zio.http._

import example.auth.digest.core.DigestAlgorithm._
import example.auth.digest.core.QualityOfProtection.Auth
case class DigestResponse(
  response: String,
  username: String,
  realm: String,
  uri: URI,
  opaque: String,
  algorithm: DigestAlgorithm,
  qop: QualityOfProtection,
  cnonce: String,
  nonce: String,
  nc: NC,
  userhash: Boolean,
)

object DigestResponse {
  def fromHeader(digest: Header.Authorization.Digest): DigestResponse = {
    DigestResponse(
      response = digest.response,
      username = digest.username,
      realm = digest.realm,
      uri = digest.uri,
      opaque = digest.opaque,
      algorithm = fromString(digest.algorithm).getOrElse(MD5),
      qop = QualityOfProtection.fromString(digest.qop).getOrElse(Auth),
      cnonce = digest.cnonce,
      nonce = digest.nonce,
      nc = NC(digest.nc),
      userhash = digest.userhash,
    )
  }
}
