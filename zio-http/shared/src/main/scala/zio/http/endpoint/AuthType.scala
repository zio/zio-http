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

  private var authScopes: Option[List[String]]  = scala.None
  def addScopes(scopes: List[String]): AuthType = {
    authScopes = Some(scopes)
    self
  }
  def getScopes: Option[List[String]]           = authScopes
}

object AuthType {

  type None   = None.type
  type Basic  = Basic.type
  type Bearer = Bearer.type

  // private var authScopes: Option[List[String]] = scala.None
  // def addScopes(scopes: List[String]): Unit = {
  //   authScopes = Some(scopes)
  // }
  // def getScopes: Option[List[String]] = authScopes

  case object None extends AuthType {
    type ClientRequirement = Unit
    override val codec: HeaderCodec[Unit] = HttpCodec.empty.asInstanceOf[HeaderCodec[Unit]]
  }

  case object Basic  extends AuthType {
    type ClientRequirement = Header.Authorization.Basic
    override val codec: HeaderCodec[Header.Authorization.Basic] =
      HeaderCodec.basicAuth
  }
  case object Bearer extends AuthType {
    type ClientRequirement = Header.Authorization.Bearer
    override val codec: HeaderCodec[Header.Authorization.Bearer] =
      HeaderCodec.bearerAuth
  }

  case object Digest extends AuthType {
    type ClientRequirement = Header.Authorization.Digest
    override val codec: HeaderCodec[Header.Authorization.Digest] =
      HeaderCodec.digestAuth
  }

  final case class Custom[ClientReq](override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq])
      extends AuthType {
    type ClientRequirement = ClientReq
  }

  final case class Or[ClientReq1, ClientReq2, ClientReq](
    auth1: AuthType { type ClientRequirement = ClientReq1 },
    auth2: AuthType { type ClientRequirement = ClientReq2 },
    alternator: Alternator.WithOut[ClientReq1, ClientReq2, ClientReq],
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] =
      auth1.codec.orElseEither(auth2.codec)(alternator)
  }
}
