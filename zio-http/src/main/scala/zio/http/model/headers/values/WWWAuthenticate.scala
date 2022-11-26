package zio.http.model.headers.values

sealed trait WWWAuthenticate

object WWWAuthenticate {
  final case class Basic(realm: String)                                        extends WWWAuthenticate
  final case class Bearer(
    realm: String,
    scope: Option[String] = None,
    error: Option[String] = None,
    errorDescription: Option[String] = None,
  ) extends WWWAuthenticate
  final case class Digest(
    realm: String,
    domain: Option[String] = None,
    nonce: Option[String] = None,
    opaque: Option[String] = None,
    stale: Option[Boolean] = None,
    algorithm: Option[String] = None,
    qop: Option[String] = None,
    charset: Option[String] = None,
    userhash: Option[Boolean] = None,
  ) extends WWWAuthenticate
  final case class HOBA(realm: Option[String], challenge: String, maxAge: Int) extends WWWAuthenticate
  final case class Mutual(realm: String, error: Option[String] = None, errorDescription: Option[String] = None)
      extends WWWAuthenticate
  final case class Negotiate(authData: Option[String] = None)                  extends WWWAuthenticate

  final case class SCRAM(
    realm: String,
    sid: String,
    data: String,
  ) extends WWWAuthenticate
  final case class `AWS4-HMAC-SHA256`(
    realm: String,
    credentials: Option[String] = None,
    signedHeaders: String,
    signature: String,
  ) extends WWWAuthenticate
  final case class Unknown(scheme: String, realm: String, params: Map[String, String]) extends WWWAuthenticate

}
