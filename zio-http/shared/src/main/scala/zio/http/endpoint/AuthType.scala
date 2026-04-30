package zio.http.endpoint

import zio.NonEmptyChunk

import zio.http._
import zio.http.codec._

/**
 * Describes the authentication type for an [[Endpoint]].
 *
 * When authentication fails at runtime, the endpoint responds with
 * [[unauthorizedStatus]], which defaults to `Status.NotFound` (404). This is an
 * intentional security pattern (information hiding) that prevents attackers
 * from discovering protected resources. To use a different status:
 *
 * {{{
 * Endpoint(Method.GET / "secret")
 *   .auth(AuthType.Bearer)
 *   .unauthorizedStatus(Status.Unauthorized) // respond with 401 instead
 * }}}
 */
sealed trait AuthType { self =>
  type ClientRequirement
  def codec: HttpCodec[HttpCodecType.RequestType, ClientRequirement]

  /**
   * The HTTP status code returned when authentication fails. Defaults to
   * `Status.NotFound` (404) for security (information hiding). Override with
   * [[withUnauthorizedStatus]] to use e.g. `Status.Unauthorized` (401).
   */
  def unauthorizedStatus: Status = Status.NotFound

  /**
   * Returns a copy of this auth type with a different unauthorized status.
   *
   * {{{
   * AuthType.Bearer.withUnauthorizedStatus(Status.Unauthorized)
   * }}}
   */
  def withUnauthorizedStatus(status: Status): AuthType =
    AuthType
      .WithStatus(self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }], status)
      .asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }]

  def |[ClientReq2, ClientReq](that: AuthType { type ClientRequirement = ClientReq2 })(implicit
    alternator: Alternator.WithOut[ClientRequirement, ClientReq2, ClientReq],
  ): AuthType { type ClientRequirement = ClientReq } =
    AuthType
      .Or(self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }], that, alternator)
      .asInstanceOf[
        AuthType { type ClientRequirement = ClientReq },
      ]

}

object AuthType {

  type None   = None.type
  type Basic  = Basic.type
  type Bearer = Bearer.type

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

  /**
   * Authentication via a named cookie. The handler / context middleware
   * receives the cookie value as a `String`. If the named cookie is missing
   * from the request, the codec fails to decode and the endpoint responds
   * with [[AuthType.unauthorizedStatus]] (404 by default; override to 401
   * with `.unauthorizedStatus(Status.Unauthorized)` if you prefer the
   * standards-conformant response).
   *
   * If the request carries multiple cookies with the same name (rare but
   * valid -- e.g. one per `Domain`/`Path` scope), the first one in header
   * order wins.
   *
   * Example: combine with `Bearer` so an endpoint accepts either an
   * `Authorization` header or a session cookie:
   *
   * {{{
   * Endpoint(Method.GET / "download" / fileId)
   *   .auth(AuthType.Bearer | AuthType.Cookie("session"))
   *   .unauthorizedStatus(Status.Unauthorized)
   * }}}
   */
  final case class Cookie(name: String) extends AuthType {
    type ClientRequirement = String
    override val codec: HttpCodec[HttpCodecType.RequestType, String] =
      HeaderCodec.cookie.transformOrFailLeft[String] { cookieHeader =>
        cookieHeader.value.collectFirst { case c if c.name == name => c.content }
          .toRight(s"Missing cookie '$name'")
      } { value =>
        Header.Cookie(NonEmptyChunk.single(zio.http.Cookie.Request(name, value)))
      }
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

    override def unauthorizedStatus: Status = auth1.unauthorizedStatus
  }

  final case class WithStatus[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    override val unauthorizedStatus: Status,
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] = authType.codec
  }

  final case class ScopedAuth[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    _scopes: List[String],
  ) extends AuthType {
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] = authType.codec

    override def unauthorizedStatus: Status = authType.unauthorizedStatus

    def scopes: List[String] = _scopes

    def scopes(newScopes: List[String]) = copy(_scopes = newScopes)
  }
}
