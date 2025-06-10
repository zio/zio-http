package zio.http.endpoint

import zio.http._
import zio.http.codec._

sealed trait AuthType { self =>
  type ClientRequirement
  def codec: HttpCodec[HttpCodecType.RequestType, ClientRequirement]

  def |[ClientReq2, ClientReq](that: AuthType { type ClientRequirement = ClientReq2 })(implicit
    alternator: Alternator.WithOut[ClientRequirement, ClientReq2, ClientReq],
  ): AuthType { type ClientRequirement = ClientReq } =
    AuthType
      .Or(self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }], that, alternator)
      .asInstanceOf[
        AuthType { type ClientRequirement = ClientReq },
      ]

  def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate]
}

object AuthType {

  type None   = None.type
  type Basic  = Basic.type
  type Bearer = Bearer.type

  case object None extends AuthType {
    type ClientRequirement = Unit
    override val codec: HeaderCodec[Unit] = HttpCodec.empty.asInstanceOf[HeaderCodec[Unit]]

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      Option.empty
  }

  case object Basic  extends AuthType {
    type ClientRequirement = Header.Authorization.Basic
    override val codec: HeaderCodec[Header.Authorization.Basic] =
      HeaderCodec.basicAuth

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      Some(Header.WWWAuthenticate.Basic())
  }
  case object Bearer extends AuthType {
    type ClientRequirement = Header.Authorization.Bearer
    override val codec: HeaderCodec[Header.Authorization.Bearer] =
      HeaderCodec.bearerAuth

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      Some(Header.WWWAuthenticate.Bearer(???))
  }

  case object Digest extends AuthType {
    type ClientRequirement = Header.Authorization.Digest
    override val codec: HeaderCodec[Header.Authorization.Digest] =
      HeaderCodec.digestAuth

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      Some(Header.WWWAuthenticate.Digest(None))
  }

  final case class Custom[ClientReq](override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq])
      extends AuthType {
    type ClientRequirement = ClientReq

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      Some(Header.WWWAuthenticate.Unknown(???, ???, ???))
  }

  final case class Or[ClientReq1, ClientReq2, ClientReq](
    auth1: AuthType { type ClientRequirement = ClientReq1 },
    auth2: AuthType { type ClientRequirement = ClientReq2 },
    alternator: Alternator.WithOut[ClientReq1, ClientReq2, ClientReq],
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq]  =
      auth1.codec.orElseEither(auth2.codec)(alternator)
    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      auth1.asWWWAuthenticateHeader
  }

  final case class ScopedAuth[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    _scopes: List[String],
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] = authType.codec

    override def asWWWAuthenticateHeader: Option[Header.WWWAuthenticate] =
      authType.asWWWAuthenticateHeader

    def scopes: List[String] = _scopes

    def scopes(newScopes: List[String]) = copy(_scopes = newScopes)
  }
}
