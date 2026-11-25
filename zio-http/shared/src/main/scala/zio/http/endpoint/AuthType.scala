package zio.http.endpoint

import zio.http._
import zio.http.codec._

/**
 * Describes the authentication type of an [[Endpoint]]: `AuthType.None`,
 * `AuthType.Basic`, `AuthType.Bearer`, `AuthType.Digest`, `AuthType.Custom`, or
 * an alternative of two auth types built with `|`. Each auth type has a request
 * codec for the credentials and a `ClientRequirement` type, the credentials a
 * client supplies.
 *
 * The [[unauthorizedStatus]] of an auth type defaults to `Status.NotFound`
 * (404). This is an intentional security pattern (information hiding) that
 * prevents attackers from discovering protected resources. To use a different
 * status:
 *
 * {{{
 * import zio.http._
 * import zio.http.endpoint._
 *
 * Endpoint(Method.GET / "secret")
 *   .auth(AuthType.Bearer)
 *   .unauthorizedStatus(Status.Unauthorized) // 401 instead of 404
 * }}}
 */
sealed trait AuthType { self =>

  /** The type of the credentials a client supplies for this auth type. */
  type ClientRequirement

  /**
   * The request codec that reads and writes the credentials of this auth type.
   */
  def codec: HttpCodec[HttpCodecType.RequestType, ClientRequirement]

  /**
   * The HTTP status code of this auth type for failed authentication. Defaults
   * to `Status.NotFound` (404) for security (information hiding). Change it
   * with [[withUnauthorizedStatus]] to use e.g. `Status.Unauthorized` (401). An
   * `AuthType.Or` reports the status of its first alternative; an
   * `AuthType.ScopedAuth` reports the status of the auth type it wraps.
   */
  def unauthorizedStatus: Status = Status.NotFound

  /**
   * Returns an `AuthType.WithStatus` that wraps this auth type and reports
   * `status` as its [[unauthorizedStatus]]. The result is not equal to this
   * auth type.
   *
   * {{{
   * import zio.http._
   * import zio.http.endpoint._
   *
   * AuthType.Bearer.withUnauthorizedStatus(Status.Forbidden)
   * }}}
   */
  def withUnauthorizedStatus(status: Status): AuthType =
    AuthType
      .WithStatus(self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }], status)
      .asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }]

  /**
   * Returns an `AuthType.Or` that offers this auth type and `that` as
   * alternatives. Its `ClientRequirement` is chosen by the implicit
   * `Alternator`, and its [[unauthorizedStatus]] is the one of this auth type.
   */
  def |[ClientReq2, ClientReq](that: AuthType { type ClientRequirement = ClientReq2 })(implicit
    alternator: Alternator.WithOut[ClientRequirement, ClientReq2, ClientReq],
  ): AuthType { type ClientRequirement = ClientReq } =
    AuthType
      .Or(self.asInstanceOf[AuthType { type ClientRequirement = self.ClientRequirement }], that, alternator)
      .asInstanceOf[
        AuthType { type ClientRequirement = ClientReq },
      ]

}

/**
 * The built-in auth types. `None`, `Basic`, `Bearer` and `Digest` are objects
 * with a type alias of the same name where one exists, `Custom` wraps a request
 * codec of your own, and `Or`, `WithStatus` and `ScopedAuth` wrap other auth
 * types.
 */
object AuthType {

  /** The type of the `None` object. */
  type None = None.type

  /** The type of the `Basic` object. */
  type Basic = Basic.type

  /** The type of the `Bearer` object. */
  type Bearer = Bearer.type

  /**
   * No authentication. Its client requirement is `Unit` and its codec is empty.
   */
  case object None extends AuthType {

    /** `Unit`: a client supplies no credentials. */
    type ClientRequirement = Unit
    override val codec: HeaderCodec[Unit] = HttpCodec.empty.asInstanceOf[HeaderCodec[Unit]]
  }

  /** HTTP Basic authentication, read from the `Authorization` header. */
  case object Basic extends AuthType {

    /** The `Authorization` header value of a Basic credential. */
    type ClientRequirement = Header.Authorization.Basic
    override val codec: HeaderCodec[Header.Authorization.Basic] =
      HeaderCodec.basicAuth
  }

  /** HTTP Bearer token authentication, read from the `Authorization` header. */
  case object Bearer extends AuthType {

    /** The `Authorization` header value of a Bearer credential. */
    type ClientRequirement = Header.Authorization.Bearer
    override val codec: HeaderCodec[Header.Authorization.Bearer] =
      HeaderCodec.bearerAuth
  }

  /** HTTP Digest authentication, read from the `Authorization` header. */
  case object Digest extends AuthType {

    /** The `Authorization` header value of a Digest credential. */
    type ClientRequirement = Header.Authorization.Digest
    override val codec: HeaderCodec[Header.Authorization.Digest] =
      HeaderCodec.digestAuth
  }

  /**
   * An auth type defined by `codec`, a request codec for the credentials. The
   * decoded value of `codec` is the client requirement.
   */
  final case class Custom[ClientReq](override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq])
      extends AuthType {

    /** The value type of the custom codec. */
    type ClientRequirement = ClientReq
  }

  /**
   * Two auth types as alternatives. Its codec is `auth1.codec` or else
   * `auth2.codec`, combined by `alternator`, and its unauthorized status is the
   * one of `auth1`. Created by `AuthType#|`.
   */
  final case class Or[ClientReq1, ClientReq2, ClientReq](
    auth1: AuthType { type ClientRequirement = ClientReq1 },
    auth2: AuthType { type ClientRequirement = ClientReq2 },
    alternator: Alternator.WithOut[ClientReq1, ClientReq2, ClientReq],
  ) extends AuthType {

    /** The client requirement chosen by `alternator`. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] =
      auth1.codec.orElseEither(auth2.codec)(alternator)

    override def unauthorizedStatus: Status = auth1.unauthorizedStatus
  }

  /**
   * Wraps `authType` with its own `unauthorizedStatus`; the codec is the codec
   * of `authType`. Created by `AuthType#withUnauthorizedStatus`.
   */
  final case class WithStatus[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    override val unauthorizedStatus: Status,
  ) extends AuthType {

    /** The client requirement of the wrapped auth type. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] = authType.codec
  }

  /**
   * Wraps `authType` with a list of scopes; the codec and the unauthorized
   * status are those of `authType`. Created by `Endpoint#scopes`.
   */
  final case class ScopedAuth[ClientReq](
    authType: AuthType { type ClientRequirement = ClientReq },
    _scopes: List[String],
  ) extends AuthType {

    /** The client requirement of the wrapped auth type. */
    type ClientRequirement = ClientReq
    override val codec: HttpCodec[HttpCodecType.RequestType, ClientReq] = authType.codec

    override def unauthorizedStatus: Status = authType.unauthorizedStatus

    /** Returns the scopes of this auth type. */
    def scopes: List[String] = _scopes

    /**
     * Returns a copy of this auth type with `newScopes` in place of its scopes.
     */
    def scopes(newScopes: List[String]) = copy(_scopes = newScopes)
  }
}
