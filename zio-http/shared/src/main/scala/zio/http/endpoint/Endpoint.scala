/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import scala.annotation.nowarn
import scala.reflect.ClassTag

import zio._
import zio.json.ast.Json

import zio.stream.ZStream

import zio.schema.Schema

import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec.HttpCodecError.EncodingResponseError
import zio.http.codec._
import zio.http.endpoint.Endpoint.{OutErrors, defaultMediaTypes}
import zio.http.internal.StringBuilderPool

/**
 * An `Endpoint` describes one HTTP API operation as data: its route pattern,
 * the request input it reads (path parameters, query parameters, headers and
 * body), the output and error types it writes in the response, its
 * authentication type and its documentation. `Endpoint(route)` creates one, and
 * builder methods such as `query`, `header`, `in`, `out`, `outError` and `auth`
 * each return a new endpoint with one more part.
 *
 * The type parameters are the type of the path parameters (`PathInput`), the
 * type of the whole request input (`Input`), the error type (`Err`), the output
 * type (`Output`) and the authentication type (`Auth`). `Input` holds the
 * values of all input parts, as a tuple when there are several; a part of type
 * `Unit`, such as a literal path segment, adds no element.
 *
 * Because an endpoint is a description, one value serves several uses:
 * `implement` turns it into a server [[zio.http.Route]], `OpenAPIGen` turns it
 * into an OpenAPI document, and `apply` makes an [[Invocation]] that an
 * [[EndpointExecutor]] sends as a client request.
 *
 * {{{
 * import zio._
 * import zio.http._
 *
 * val getUser: Endpoint[Int, (Int, Boolean), String, String, AuthType.None] =
 *   Endpoint(Method.GET / "users" / int("id"))
 *     .query[Boolean]("verbose")
 *     .out[String]
 *     .outError[String](Status.NotFound)
 *
 * val route: Route[Any, Nothing] =
 *   getUser.implement { case (id, verbose) =>
 *     if (id == 7) ZIO.succeed(if (verbose) "Ada Lovelace, id 7" else "Ada Lovelace")
 *     else ZIO.fail("no user with id " + id)
 *   }
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/concepts/endpoint/ Endpoint]]
 */
@nowarn("msg=type parameter .* defined")
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[HttpCodecType.RequestType, Input],
  output: HttpCodec[HttpCodecType.ResponseType, Output],
  error: HttpCodec[HttpCodecType.ResponseType, Err],
  codecError: HttpCodec[HttpCodecType.ResponseType, HttpCodecError],
  documentation: Doc,
  authType: Auth,
) extends EndpointPlatformSpecific[PathInput, Input, Err, Output, Auth] {
  self =>

  /**
   * The `Combiner` that joins `Input` with the credentials that a client sends
   * for `authType` (`authType.ClientRequirement`, for example
   * `Header.Authorization.Bearer` for `AuthType.Bearer`).
   */
  val authCombiner: Combiner[Input, authType.ClientRequirement] =
    implicitly[Combiner[Input, authType.ClientRequirement]]

  /**
   * The request codec of the credentials for `authType`: the `Authorization`
   * header for `AuthType.Basic`, `AuthType.Bearer` and `AuthType.Digest`, and
   * an empty codec for `AuthType.None`.
   */
  val authCodec: HttpCodec[HttpCodecType.RequestType, authType.ClientRequirement] =
    authType.codec

  private[http] def authedInput(implicit
    combiner: Combiner[Input, authType.ClientRequirement],
  ): HttpCodec[HttpCodecType.RequestType, AuthedInput] = {
    input ++ authCodec
  }.asInstanceOf[HttpCodec[HttpCodecType.RequestType, AuthedInput]]

  /**
   * The type of the input together with the client credentials: `Input` joined
   * with `authType.ClientRequirement` by `authCombiner`.
   */
  type AuthedInput = authCombiner.Out

  /**
   * Returns a new endpoint whose documentation is this endpoint's documentation
   * followed by `that`. OpenAPI generation includes it.
   */
  def ??(that: Doc): Endpoint[PathInput, Input, Err, Output, Auth] = copy(documentation = self.documentation + that)

  /**
   * Flattens out this endpoint to a chunk of alternatives. Each alternative is
   * guaranteed to not have any alternatives itself.
   */
  def alternatives: Chunk[(Endpoint[PathInput, Input, Err, Output, Auth], HttpCodec.Fallback.Condition)] =
    self.input.alternatives.map { case (input, condition) =>
      self.copy(input = input) -> condition
    }

  /**
   * Creates an [[Invocation]] of this endpoint with `input`. An
   * [[EndpointExecutor]] runs the invocation: it sends the request and decodes
   * the response into `Output` or `Err`. Overloads that take two to twelve
   * arguments build a tuple input from its elements.
   *
   * {{{
   * import zio.http._
   *
   * val search =
   *   Endpoint(Method.GET / "shops" / int("shopId"))
   *     .query[String]("q")
   *     .out[List[String]]
   *
   * val invocation = search(12, "espresso")
   * // invocation.input == (12, "espresso")
   * }}}
   */
  def apply(input: Input): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, input)

  /**
   * Creates an [[Invocation]] of this endpoint from the two elements of its
   * tuple input `(A, B)`.
   */
  def apply[A, B](a: A, b: B)(implicit
    ev: (A, B) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, ev((a, b)))

  /**
   * Creates an [[Invocation]] of this endpoint from the three elements of its
   * tuple input `(A, B, C)`.
   */
  def apply[A, B, C](a: A, b: B, c: C)(implicit
    ev: (A, B, C) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, ev((a, b, c)))

  /**
   * Creates an [[Invocation]] of this endpoint from the four elements of its
   * tuple input `(A, B, C, D)`.
   */
  def apply[A, B, C, D](a: A, b: B, c: C, d: D)(implicit
    ev: (A, B, C, D) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the five elements of its
   * tuple input `(A, B, C, D, E)`.
   */
  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E)(implicit
    ev: (A, B, C, D, E) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the six elements of its
   * tuple input `(A, B, C, D, E, F)`.
   */
  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F)(implicit
    ev: (A, B, C, D, E, F) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the seven elements of its
   * tuple input `(A, B, C, D, E, F, G)`.
   */
  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G)(implicit
    ev: (A, B, C, D, E, F, G) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the eight elements of its
   * tuple input `(A, B, C, D, E, F, G, H)`.
   */
  def apply[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H)(implicit
    ev: (A, B, C, D, E, F, G, H) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the nine elements of its
   * tuple input `(A, B, C, D, E, F, G, H, I)`.
   */
  def apply[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I)(implicit
    ev: (A, B, C, D, E, F, G, H, I) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the ten elements of its
   * tuple input `(A, B, C, D, E, F, G, H, I, J)`.
   */
  def apply[A, B, C, D, E, F, G, H, I, J](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i, j)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the eleven elements of its
   * tuple input `(A, B, C, D, E, F, G, H, I, J, K)`.
   */
  def apply[A, B, C, D, E, F, G, H, I, J, K](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
  )(implicit
    ev: (A, B, C, D, E, F, G, H, I, J, K) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i, j, k)),
    )

  /**
   * Creates an [[Invocation]] of this endpoint from the twelve elements of its
   * tuple input `(A, B, C, D, E, F, G, H, I, J, K, L)`.
   */
  def apply[A, B, C, D, E, F, G, H, I, J, K, L](
    a: A,
    b: B,
    c: C,
    d: D,
    e: E,
    f: F,
    g: G,
    h: H,
    i: I,
    j: J,
    k: K,
    l: L,
  )(implicit
    ev: (A, B, C, D, E, F, G, H, I, J, K, L) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i, j, k, l)),
    )

  /** Sets the authentication type for this endpoint. */
  def auth[Auth0 <: AuthType](auth: Auth0): Endpoint[PathInput, Input, Err, Output, Auth0] =
    copy(authType = auth)

  /**
   * Overrides the HTTP status returned when authentication fails. Defaults to
   * 404 (Not Found) for security. Use `Status.Unauthorized` for standard 401
   * behavior.
   */
  def unauthorizedStatus(status: Status): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(authType = authType.withUnauthorizedStatus(status).asInstanceOf[Auth])

  /**
   * Renders the URL path for this endpoint given the path input values.
   *
   * Example:
   * {{{
   *   import zio.http._
   *
   *   val ep = Endpoint(Method.GET / "users" / int("id"))
   *   ep.renderUrl(42) // Right("/users/42")
   * }}}
   */
  def renderUrl(values: PathInput): Either[String, String] =
    route.format(values).map(_.encode)

  /**
   * Renders the URL path for this endpoint with a base path prefix. One
   * trailing `/` of `basePath` is dropped: with the base path
   * `http://localhost:8080/` and the route `GET /users/{id}`, the input `42`
   * renders as `http://localhost:8080/users/42`.
   */
  def renderUrl(basePath: String, values: PathInput): Either[String, String] =
    renderUrl(values).map { url =>
      val base = if (basePath.endsWith("/")) basePath.dropRight(1) else basePath
      base + url
    }

  /**
   * Returns the scopes set with `scopes(...)`, read from the
   * `AuthType.ScopedAuth` of this endpoint and from both sides of an
   * `AuthType.Or`. Returns an empty list when no scopes are set. It does not
   * look inside the auth type that `unauthorizedStatus` wraps, so it returns an
   * empty list after `.scopes("read").unauthorizedStatus(Status.Unauthorized)`.
   */
  def scopes: List[String] = authScopesRecursive(authType)

  private def authScopesRecursive(authType: AuthType): List[String] = authType match {
    case AuthType.ScopedAuth(nestedAuth, _) =>
      authType.asInstanceOf[AuthType.ScopedAuth[_]].scopes ++ authScopesRecursive(nestedAuth)
    case AuthType.Or(auth1, auth2, _)       =>
      authScopesRecursive(auth1) ++ authScopesRecursive(auth2)
    case _                                  =>
      Nil
  }

  /**
   * Returns a new endpoint whose auth type carries the given scopes, which
   * OpenAPI generation lists in the endpoint's security requirement. When the
   * auth type is already an `AuthType.ScopedAuth`, its scopes are replaced;
   * otherwise the auth type is wrapped in one. The `Auth` type of the result is
   * `AuthType`.
   *
   * Throws an `IllegalArgumentException` with the message "Scopes cannot be
   * empty, and authType must not be AuthType.None" when no scope is given or
   * when the auth type is `AuthType.None`.
   */
  def scopes(scopes: String*): Endpoint[PathInput, Input, Err, Output, AuthType] =
    if (scopes.isEmpty || authType == AuthType.None) {
      throw new IllegalArgumentException("Scopes cannot be empty, and authType must not be AuthType.None")
    } else {
      authType match {
        case AuthType.ScopedAuth(_, _) =>
          copy(authType = authType.asInstanceOf[AuthType.ScopedAuth[_]].scopes(scopes.toList))
        case _                         =>
          copy(authType = AuthType.ScopedAuth(authType, scopes.toList))
      }
    }

  /**
   * Returns a new endpoint that answers a request that fails to decode (for
   * example one without a required query parameter) with status 400 Bad Request
   * and an empty body, instead of a body that names the problem. It replaces
   * the codec error codec, including codecs added with `outCodecError`.
   */
  def emptyErrorResponse: Endpoint[PathInput, Input, Err, Output, Auth] =
    self.copy(codecError =
      StatusCodec.BadRequest
        .transformOrFail[HttpCodecError](_ => Right(HttpCodecError.CustomError("Empty", "empty")))(_ => Right(())),
    )

  /**
   * Returns a new endpoint with named examples of the whole request input,
   * which OpenAPI generation shows. When a later call repeats a name, the value
   * from the earlier call is kept.
   */
  def examplesIn(examples: (String, Input)*): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(input = self.input.examples(examples))

  /**
   * Returns the named request input examples set by `examplesIn`. It reads only
   * the outermost codec of `input`, so it returns an empty map when an input
   * part (for example a header) was added after the last `examplesIn`.
   */
  def examplesIn: Map[String, Input] = self.input.examples

  /**
   * Returns a new endpoint with named examples of the output, which OpenAPI
   * generation shows. When a later call repeats a name, the value from the
   * earlier call is kept.
   */
  def examplesOut(examples: (String, Output)*): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(output = self.output.examples(examples))

  /**
   * Returns the named output examples set by `examplesOut`. It reads only the
   * outermost codec of `output`, so it returns an empty map when an output part
   * (for example a response header) was added after the last `examplesOut`.
   */
  def examplesOut: Map[String, Output] = self.output.examples

  /**
   * Returns a new endpoint that also reads the request header described by
   * `codec` and adds its value to the input. A request without a required
   * header gets status 400 Bad Request.
   */
  def header[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ codec)

  /**
   * Returns a new endpoint that also reads the request header `name` as an `A`,
   * decoded with its `Schema`, and adds it to the input. A request without the
   * header gets status 400 Bad Request, unless `A` is an `Option`: then the
   * value is `None`.
   */
  def header[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Input, A]): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    header(HeaderCodec.headerAs[A](name))

  /**
   * Returns a new endpoint that also reads several request headers into one `A`
   * and adds it to the input. For a case class, each field is read from the
   * header with the field's name; for a type that is not a record, the value is
   * read from the header named `headers`. When a field's header is missing, the
   * field gets an empty value (for example `""` for a `String` field and `0`
   * for an `Int` field) instead of a 400 response.
   */
  def header[A](implicit
    schema: Schema[A],
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    header(HeaderCodec.headers[A])

  /**
   * Converts this endpoint into a server [[zio.http.Route]] that decodes each
   * request into `Input`, runs `f`, and encodes its success with the output
   * codec and its failure with the error codec. A request that fails to decode
   * gets status 400 Bad Request with a body that names the problem.
   */
  def implement[Env](f: Input => ZIO[Env, Err, Output])(implicit
    trace: Trace,
  ): Route[Env, Nothing] =
    implementHandler(Handler.fromFunctionZIO(f))

  /**
   * Like `implement`, for a function that also needs a `Scope`. The scope is
   * the one the server opens for the request, so finalizers added in `f` run
   * when the server closes it.
   */
  def implementScoped[Env, Env1](f: Input => ZIO[Env, Err, Output])(implicit
    ev: Env <:< Env1 with Scope,
    trace: Trace,
  ): Route[Env1, Nothing] =
    implementHandler[Env1](
      Handler.scoped[Env1](Handler.fromFunctionZIO(f.asInstanceOf[Input => ZIO[Env1 with Scope, Err, Output]])),
    )

  /**
   * Converts this endpoint into a server route from a pure function: `Right`
   * becomes the output response and `Left` the error response.
   */
  def implementEither(f: Input => Either[Err, Output])(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fromFunctionEither[Input](f))

  /**
   * Converts this endpoint into a server route from a pure function that always
   * succeeds.
   */
  def implementPurely(f: Input => Output)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fromFunctionExit[Input](in => Exit.succeed(f(in))))

  /**
   * Converts this endpoint into a server route that responds with `output` to
   * every request that decodes.
   */
  def implementAs(output: Output)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.succeed(output))

  /**
   * Converts this endpoint into a server route that runs the effect `output`
   * for every request that decodes, without looking at the input.
   */
  def implementAsZIO[Env](output: ZIO[Env, Err, Output])(implicit
    trace: Trace,
  ): Route[Env, Nothing] =
    implementHandler(Handler.fromZIO(output))

  /**
   * Like `implementAsZIO`, for an effect that also needs a `Scope`. The scope
   * is the one the server opens for the request.
   */
  def implementAsZIOScoped[Env, Env1](output: ZIO[Env, Err, Output])(implicit
    ev: Env <:< Env1 with Scope,
    trace: Trace,
  ): Route[Env1, Nothing] =
    implementHandler[Env1](
      Handler.scoped[Env1](Handler.fromZIO(output.asInstanceOf[ZIO[Env1 with Scope, Err, Output]])),
    )

  /**
   * Converts this endpoint into a server route that fails every request that
   * decodes with `err`, encoded by the error codec.
   */
  def implementAsError(err: Err)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fail(err))

  /**
   * Converts this endpoint into a server [[zio.http.Route]] from a
   * [[zio.http.Handler]] that maps `Input` to `Output`. The other `implement`
   * methods call it.
   *
   * For each request, the route tries the input alternatives in order, decodes
   * the request together with the credentials of the auth type, runs
   * `original`, and encodes the result in a media type chosen from the `Accept`
   * header and the media types the codec supports. Decoding failures are
   * encoded with the codec error codec (by default status 400 Bad Request and a
   * JSON body).
   */
  def implementHandler[Env](original: Handler[Env, Err, Input, Output])(implicit trace: Trace): Route[Env, Nothing] = {
    import HttpCodecError.asHttpCodecError

    def authCodec(authType: AuthType): HttpCodec[HttpCodecType.RequestType, Unit] = authType match {
      case AuthType.None                => HttpCodec.empty
      case AuthType.Basic               =>
        HeaderCodec.authorization.transformOrFail {
          case Header.Authorization.Basic(_, _) => Right(())
          case _                                => Left("Basic auth required")
        } { case () =>
          Left("Unsupported")
        }
      case AuthType.Bearer              =>
        HeaderCodec.authorization.transformOrFail {
          case Header.Authorization.Bearer(_) => Right(())
          case _                              => Left("Bearer auth required")
        } { case () =>
          Left("Unsupported")
        }
      case AuthType.Digest              =>
        HeaderCodec.authorization.transformOrFail {
          case _: Header.Authorization.Digest => Right(())
          case _                              => Left("Digest auth required")
        } { case () =>
          Left("Unsupported")
        }
      case AuthType.Custom(codec)       =>
        codec.transformOrFailRight[Unit](_ => ())(_ => Left("Unsupported"))
      case AuthType.Or(auth1, auth2, _) =>
        authCodec(auth1).orElseEither(authCodec(auth2))(Alternator.leftRightEqual[Unit])
      case AuthType.ScopedAuth(auth, _) =>
        authCodec(auth)
      case AuthType.WithStatus(auth, _) =>
        authCodec(auth)
    }

    val maybeUnauthedResponse = authType.asInstanceOf[AuthType] match {
      case AuthType.None => None
      case auth          =>
        val resp = auth.unauthorizedStatus match {
          case Status.Unauthorized =>
            val wwwAuth = (auth match {
              case AuthType.Basic            => Header.WWWAuthenticate.Basic()
              case AuthType.Bearer           => Header.WWWAuthenticate.Bearer(realm = "")
              case AuthType.Digest           => Header.WWWAuthenticate.Digest(realm = Some(""))
              case AuthType.WithStatus(a, _) =>
                (a: AuthType) match {
                  case AuthType.Basic  => Header.WWWAuthenticate.Basic()
                  case AuthType.Bearer => Header.WWWAuthenticate.Bearer(realm = "")
                  case AuthType.Digest => Header.WWWAuthenticate.Digest(realm = Some(""))
                  case _               => Header.WWWAuthenticate.Bearer(realm = "")
                }
              case _                         => Header.WWWAuthenticate.Bearer(realm = "")
            }): Header.WWWAuthenticate
            Response(status = Status.Unauthorized, headers = Headers(wwwAuth))
          case status              =>
            Response(status = status)
        }
        Some(Handler.succeed(resp))
    }

    def handlers(
      config: CodecConfig,
    ): Chunk[(Handler[Env, Nothing, Request, Response], HttpCodec.Fallback.Condition)] = {
      def handleEncodingBodyErrorHandling(request: Request) =
        codecError.encodeResponse(
          EncodingResponseError,
          (
            request.headers
              .getAll(Header.Accept)
              .flatMap(_.mimeTypes) :+ MediaTypeWithQFactor(MediaType.application.`json`, Some(0.0))
          ).nonEmptyOrElse(defaultMediaTypes)(ZIO.identityFn),
          config,
        )

      def encodeWithFallback(encode: => Response, fallback: => Response) = {
        try {
          val result = encode
          Exit.succeed(result)
        } catch {
          case t: Throwable =>
            ZIO.logErrorCause("Encoding failed", Cause.fail(t)) *>
              Exit.succeed(fallback)
        }
      }

      self.alternatives.map { case (endpoint, condition) =>
        Handler.fromFunctionZIO { (request: zio.http.Request) =>
          val outputMediaTypes =
            request.headers
              .getAll(Header.Accept)
              .flatMap(_.mimeTypes)
              .nonEmptyOrElse(defaultMediaTypes)(ZIO.identityFn)

          (endpoint.input ++ authCodec(endpoint.authType)).decodeRequest(request, config).orDie.flatMap { value =>
            original(value)
              .asInstanceOf[ZIO[Env, Err, Output]]
              .foldZIO(
                success = output =>
                  encodeWithFallback(
                    endpoint.output.encodeResponse(output, outputMediaTypes, config),
                    handleEncodingBodyErrorHandling(request),
                  ),
                failure = error =>
                  encodeWithFallback(
                    endpoint.error.encodeResponse(error, outputMediaTypes, config),
                    handleEncodingBodyErrorHandling(request),
                  ),
              )
          }
        } -> condition
      }
    }

    // TODO: What to do if there are no endpoints??
    def handlers2(
      handlers: Chunk[(Handler[Env, Nothing, Request, Response], HttpCodec.Fallback.Condition)],
    ): NonEmptyChunk[(Handler[Env, Response, Request, Response], HttpCodec.Fallback.Condition)] = {
      def noFound: NonEmptyChunk[(Handler[Env, Response, Request, Response], HttpCodec.Fallback.Condition)] =
        NonEmptyChunk(
          Handler.fail(zio.http.Response(status = Status.NotFound)) -> HttpCodec.Fallback.Condition.IsHttpCodecError,
        )

      handlers.nonEmptyOrElse(ifEmpty = noFound)(ZIO.identityFn)
    }

    val handler =
      Handler.fromZIO(CodecConfig.codecRef.get).flatMap { config =>
        val hdlrs = handlers(config)
        hdlrs.tail
          .foldLeft(handlers2(hdlrs).head._1) { case (acc, (handler, condition)) =>
            acc.catchAllCause { cause =>
              if (condition(cause)) {
                handler
              } else {
                Handler.failCause(cause)
              }
            }
          }
          .catchAllCause { cause =>
            asHttpCodecError(cause) match {
              case Some(HttpCodecError.CustomError("SchemaTransformationFailure", message))
                  if maybeUnauthedResponse.isDefined && message.endsWith(" auth required") =>
                maybeUnauthedResponse.get
              case Some(e: HttpCodecError.MissingHeader)
                  if maybeUnauthedResponse.isDefined && e.headerName.toLowerCase == "authorization" =>
                maybeUnauthedResponse.get
              case Some(_) =>
                Handler.fromFunctionZIO { (request: zio.http.Request) =>
                  val error    = cause.defects.head.asInstanceOf[HttpCodecError]
                  val response = {
                    val outputMediaTypes =
                      (
                        request.headers
                          .getAll(Header.Accept)
                          .flatMap(_.mimeTypes) :+ MediaTypeWithQFactor(MediaType.application.`json`, Some(0.0))
                      ).nonEmptyOrElse(defaultMediaTypes)(ZIO.identityFn)

                    codecError.encodeResponse(error, outputMediaTypes, config)
                  }
                  ZIO.succeed(response)
                }
              case None    =>
                Handler.failCause(cause)
            }
          }
      }

    Route.handledIgnoreParams(self.route)(handler)
  }

  /**
   * Like `implementHandler`, for a handler that also needs a `Scope`. The scope
   * is the one the server opens for the request.
   */
  def implementHandlerScoped[Env, Env1](original: Handler[Env, Err, Input, Output])(implicit
    ev: Env <:< Env1 with Scope,
    trace: Trace,
  ): Route[Env1, Nothing] =
    implementHandler[Env1](Handler.scoped[Env1](original.asInstanceOf[Handler[Env1 with Scope, Err, Input, Output]]))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2`,
   * with the `HttpContentCodec` of `Input2`, and adds it to the input. When an
   * endpoint has more than one body, the request is sent as
   * `multipart/form-data` with one part per body.
   *
   * {{{
   * import zio.ZNothing
   * import zio.http._
   *
   * val rename: Endpoint[Int, (Int, String), ZNothing, Unit, AuthType.None] =
   *   Endpoint(Method.PUT / "teams" / int("teamId") / "name")
   *     .in[String]
   *     .out[Unit]
   * }}}
   */
  def in[Input2: HttpContentCodec](implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2])

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` and
   * adds it to the input, with `doc` as the body's documentation.
   */
  def in[Input2: HttpContentCodec](doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2] ?? doc)

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` and
   * adds it to the input. `name` is the name of the body's part when the
   * request is `multipart/form-data`.
   */
  def in[Input2: HttpContentCodec](name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2](name))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` and
   * adds it to the input. `name` is the name of the body's part when the
   * request is `multipart/form-data`, and `doc` is the body's documentation.
   */
  def in[Input2: HttpContentCodec](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content[Input2](name) ?? doc))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` in
   * the media type `mediaType` and adds it to the input. It throws an
   * `IllegalArgumentException` when the codec of `Input2` does not support
   * `mediaType`.
   */
  def in[Input2: HttpContentCodec](mediaType: MediaType)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2](mediaType))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` in
   * the media type `mediaType` and adds it to the input, with `doc` as the
   * body's documentation. It throws an `IllegalArgumentException` when the
   * codec of `Input2` does not support `mediaType`.
   */
  def in[Input2: HttpContentCodec](mediaType: MediaType, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content(mediaType) ?? doc))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` in
   * the media type `mediaType` and adds it to the input. `name` is the name of
   * the body's part when the request is `multipart/form-data`. It throws an
   * `IllegalArgumentException` when the codec of `Input2` does not support
   * `mediaType`.
   */
  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content(name, mediaType))

  /**
   * Returns a new endpoint that also reads the request body as an `Input2` in
   * the media type `mediaType` and adds it to the input. `name` is the name of
   * the body's part when the request is `multipart/form-data`, and `doc` is the
   * body's documentation. It throws an `IllegalArgumentException` when the
   * codec of `Input2` does not support `mediaType`.
   */
  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content(name, mediaType) ?? doc))

  /**
   * Returns a new endpoint derived from this one, whose request content is
   * decoded from `application/x-www-form-urlencoded` data using a zio-schema
   * [[zio.schema.Schema]].
   */
  def inForm[Input2](implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input =
      input ++ HttpCodec.content[Input2](MediaType.application.`x-www-form-urlencoded`)(
        HttpContentCodec.form.only[Input2],
      ),
    )

  /**
   * Returns a new endpoint derived from this one, whose request content is
   * decoded from `application/x-www-form-urlencoded` data using a zio-schema
   * [[zio.schema.Schema]] and is documented.
   */
  def inForm[Input2](doc: Doc)(implicit
    schema: Schema[Input2],
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input =
      input ++ (HttpCodec.content[Input2](MediaType.application.`x-www-form-urlencoded`)(
        HttpContentCodec.form.only[Input2],
      ) ?? doc),
    )

  /**
   * Returns a new endpoint derived from this one, whose request must satisfy
   * the specified codec.
   */
  def inCodec[Input2](codec: HttpCodec[HttpCodecType.RequestType, Input2])(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ codec)

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type.
   */
  def inStream[Input2: HttpContentCodec](implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2],
      output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: HttpContentCodec](doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2] ?? doc),
      output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type.
   */
  def inStream[Input2: HttpContentCodec](name: String)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    Endpoint(
      route,
      input = self.input ++ ContentCodec.contentStream[Input2](name),
      output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose input type is a stream
   * of the specified type and is documented.
   */
  def inStream[Input2: HttpContentCodec](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, ZStream[Any, Nothing, Input2]],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    Endpoint(
      route,
      input = self.input ++ (ContentCodec.contentStream[Input2](name) ?? doc),
      output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code.
   *
   * Each call adds one more possible response. When the endpoint already has an
   * output of another type, the new output type is `Either[Output2, Output]`,
   * and the response is encoded by the codec of the side the value is on. When
   * `Output2` is the same type as `Output`, the type stays the same and
   * responses use the codec added last.
   *
   * {{{
   * import zio.ZNothing
   * import zio.http._
   *
   * val upload: Endpoint[Unit, Unit, ZNothing, Either[Int, String], AuthType.None] =
   *   Endpoint(Method.POST / "uploads")
   *     .out[String]
   *     .out[Int](Status.Accepted)
   * }}}
   */
  def out[Output2: HttpContentCodec](implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2] ++ StatusCodec.status(Status.Ok)) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code and is documented.
   */
  def out[Output2: HttpContentCodec](doc: Doc)(implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    out[Output2](Status.Ok, doc)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code, in the media type `mediaType`.
   */
  def out[Output2: HttpContentCodec](
    mediaType: MediaType,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    out[Output2](Status.Ok, mediaType)

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2] ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code and is documented.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = ((HttpCodec.content[Output2] ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the ok status code, in the media type `mediaType`, and
   * is documented.
   */
  def out[Output2: HttpContentCodec](
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2](mediaType) ++ StatusCodec.Ok ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code, in the media type
   * `mediaType`, and is documented.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
    doc: Doc,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = ((HttpCodec.content[Output2](mediaType) ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose output type is the
   * specified type for the specified status code, in the media type
   * `mediaType`.
   */
  def out[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
  )(implicit alt: Alternator[Output2, Output]): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    Endpoint(
      route,
      input,
      output = (HttpCodec.content[Output2](mediaType) ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )

  /**
   * Returns a new endpoint derived from this one, whose response must satisfy
   * the specified codec.
   */
  def outCodec[Output2](codec: HttpCodec[HttpCodecType.ResponseType, Output2])(implicit
    alt: Alternator[Output2, Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    copy(output = codec | self.output)

  /**
   * Returns a new endpoint whose codec error codec, which encodes the
   * `HttpCodecError` of a request that fails to decode, is `codec` followed by
   * the current one. When a server route encodes the error, the alternatives
   * whose body media type matches the request's `Accept` header or JSON are
   * tried first, so a `codec` without a body is not used while the default JSON
   * codec error codec is present.
   */
  def outCodecError(
    codec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError],
  ): Endpoint[PathInput, Input, Err, Output, Auth] =
    self.copy(codecError = codec | self.codecError)

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code.
   *
   * Like `out`, each call adds one more possible error. When the endpoint
   * already has an error of another type, the new error type is
   * `Either[Err2, Err]`: a handler fails with `Left` for the new type and
   * `Right` for the earlier one.
   */
  def outError[Err2: HttpContentCodec](status: Status)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Auth] =
    copy[PathInput, Input, alt.Out, Output, Auth](
      error = (ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) | self.error,
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code and is documented.
   */
  def outError[Err2: HttpContentCodec](status: Status, doc: Doc)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Auth] =
    copy[PathInput, Input, alt.Out, Output, Auth](
      error = ((ContentCodec.content[Err2]("error-response") ++ StatusCodec.status(status)) ?? doc) | self.error,
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code with a custom media type.
   */
  def outError[Err2: HttpContentCodec](status: Status, mediaType: MediaType)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Auth] =
    copy[PathInput, Input, alt.Out, Output, Auth](
      error = (ContentCodec.content[Err2]("error-response", mediaType) ++ StatusCodec.status(status)) | self.error,
    )

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code with a custom media type and is documented.
   */
  def outError[Err2: HttpContentCodec](status: Status, mediaType: MediaType, doc: Doc)(implicit
    alt: Alternator[Err2, Err],
  ): Endpoint[PathInput, Input, alt.Out, Output, Auth] =
    copy[PathInput, Input, alt.Out, Output, Auth](
      error =
        ((ContentCodec.content[Err2]("error-response", mediaType) ++ StatusCodec.status(status)) ?? doc) | self.error,
    )

  /**
   * Starts adding one error codec per subtype of `Err2`; the
   * [[Endpoint.OutErrors]] that it returns takes the codecs. The error type of
   * the result is `Err2` when the endpoint has no error type yet, instead of a
   * nested `Either`.
   */
  def outErrors[Err2]: OutErrors[PathInput, Input, Err, Output, Auth, Err2] = OutErrors(self)

  /**
   * Returns a new endpoint whose responses also carry the header described by
   * `codec`; its value is added to the output.
   */
  def outHeader[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Output, A],
  ): Endpoint[PathInput, Input, Err, combiner.Out, Auth] =
    copy(output = self.output ++ codec)

  /**
   * Returns a new endpoint whose responses also carry the header `name`, with a
   * value of type `A` encoded with its `Schema`; the value is added to the
   * output.
   */
  def outHeader[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Output, A]): Endpoint[PathInput, Input, Err, combiner.Out, Auth] =
    outHeader(HeaderCodec.headerAs[A](name))

  /**
   * Returns a new endpoint whose responses also carry several headers, written
   * from one `A` that is added to the output: for a case class, each field
   * becomes the header with the field's name. For a type that is not a record,
   * the value is written to the header named `headers`.
   */
  def outHeader[A](implicit
    schema: Schema[A],
    combiner: Combiner[Output, A],
  ): Endpoint[PathInput, Input, Err, combiner.Out, Auth] =
    outHeader(HeaderCodec.headers[A])

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: HttpContentCodec](implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(Status.Ok)) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code.
   */
  def outStream[Output2: HttpContentCodec](doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(Status.Ok) ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code and is documented.
   */
  def outStream[Output2: HttpContentCodec](status: Status, doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec
          .binaryStream(MediaType.application.`octet-stream`)
          .asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2]
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(status) ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code, in the media type
   * `mediaType`. When `mediaType` is binary, the stream is sent as raw bytes.
   */
  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    outStream(Status.Ok, mediaType)

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the ok status code, in the media type
   * `mediaType`, and is documented. The stream is sent as raw bytes when the
   * elements are `Byte`s.
   */
  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
    doc: Doc,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    outStream(Status.Ok, mediaType, doc)

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code, in the media type
   * `mediaType`. When `mediaType` is binary, the stream is sent as raw bytes.
   */
  def outStream[Output2: HttpContentCodec](
    status: Status,
    mediaType: MediaType,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] = {
    val contentCodec =
      if (mediaType.binary)
        ContentCodec.binaryStream(mediaType).asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2](mediaType)
    Endpoint(
      route,
      input,
      output = (contentCodec ++ StatusCodec.status(status)) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )
  }

  /**
   * Returns a new endpoint derived from this one, whose output type is a stream
   * of the specified type for the specified status code, in the media type
   * `mediaType`, and is documented. The stream is sent as raw bytes when the
   * elements are `Byte`s.
   */
  def outStream[Output2: HttpContentCodec](status: Status, mediaType: MediaType, doc: Doc)(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] = {
    val contentCodec =
      if (implicitly[HttpContentCodec[Output2]].choices.forall(_._2.schema == Schema[Byte]))
        ContentCodec.binaryStream(mediaType).asInstanceOf[ContentCodec[ZStream[Any, Nothing, Output2]]]
      else ContentCodec.contentStream[Output2](mediaType)
    Endpoint(
      route,
      input,
      output = ((contentCodec ++ StatusCodec.status(status)) ?? doc) | self.output,
      error,
      codecError,
      documentation,
      authType,
    )
  }

  /**
   * Returns a new endpoint that also reads the query parameters described by
   * `codec` and adds their value to the input.
   */
  def query[A](codec: QueryCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ codec)

  /**
   * Returns a new endpoint that also reads the query parameter `name` as an
   * `A`, decoded with its `Schema`, and adds it to the input. A request without
   * the parameter gets status 400 Bad Request, unless `A` is an `Option`: then
   * the value is `None`. A value that does not decode also gets status 400.
   */
  def query[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Input, A]): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ QueryCodec.query[A](name))

  /**
   * Returns a new endpoint that also reads several query parameters into one
   * `A` and adds it to the input: for a case class, each field is read from the
   * query parameter with the field's name.
   */
  def query[A](implicit
    schema: Schema[A],
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ QueryCodec.query[A])

  /**
   * Adds tags to the endpoint. They are used for documentation generation. For
   * example to group endpoints for OpenAPI.
   */
  def tag(tag: String, tags: String*): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(documentation = documentation.tag(tag +: tags))

  /**
   * A list of tags for this endpoint.
   */
  def tags: List[String] = documentation.tags

  /**
   * Adds extensions to the endpoint. They are used for documentation
   * generation.
   *
   * The name is the bare extension name, without the OpenAPI `x-` prefix:
   * `ext("foo", value)` is emitted as `x-foo`. Passing a name that already
   * includes the prefix produces `x-x-foo`.
   */
  def ext(name: String, value: Json): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(documentation = documentation.ext(name, value))

  /**
   * A map of extensions for this endpoint.
   */
  def extensions: Map[String, Json] = documentation.extensions

  /**
   * Transforms the input of this endpoint using the specified functions. This
   * is useful to build from different http inputs a domain specific input.
   *
   * For example
   * {{{
   *   import java.util.UUID
   *   import zio.http._
   *
   *   case class ChangeUserName(userId: UUID, name: String)
   *   val endpoint =
   *   Endpoint(Method.POST / "user" / uuid("userId") / "changeName").in[String]
   *     .transformIn { case (userId, name) => ChangeUserName(userId, name) } {
   *       case ChangeUserName(userId, name) => (userId, name)
   *     }
   * }}}
   */
  def transformIn[Input1](f: Input => Input1)(
    g: Input1 => Input,
  ): Endpoint[PathInput, Input1, Err, Output, Auth] =
    copy(input = self.input.transform(f)(g))

  /**
   * Transforms the output of this endpoint using the specified functions.
   */
  def transformOut[Output1](f: Output => Output1)(
    g: Output1 => Output,
  ): Endpoint[PathInput, Input, Err, Output1, Auth] =
    copy(output = self.output.transform(f)(g))

  /**
   * Transforms the error of this endpoint using the specified functions.
   */
  def transformError[Err1](f: Err => Err1)(
    g: Err1 => Err,
  ): Endpoint[PathInput, Input, Err1, Output, Auth] =
    copy(error = self.error.transform(f)(g))

  /**
   * Generates a URL for this endpoint given the base path and the path input.
   */
  def url(basePath: String, values: PathInput): Either[Exception, URL] =
    for {
      path <- route.format(values).left.map(err => new RuntimeException(err))
      url  <- URL.decode(basePath + path.encode)
    } yield url

  /**
   * Generates a URL for this endpoint given the path input.
   */
  def urlRelative(values: PathInput): Either[Exception, URL] =
    for {
      path <- route.format(values).left.map(err => new RuntimeException(err))
      url  <- URL.decode(path.encode)
    } yield url

  /**
   * Generates an absolute URL for this endpoint from the path input, with the
   * scheme, host and port of `request`'s URL. The scheme defaults to `http` and
   * the host to `localhost` when the request URL has none.
   */
  def urlFromRequest(values: PathInput)(request: Request): Either[Exception, URL] = {
    val basePath = StringBuilderPool.withStringBuilder { sb =>
      if (request.url.scheme.isEmpty) sb.append("http")
      else sb.append(request.url.scheme.get.encode)
      sb.append("://")
      sb.append(request.url.host.getOrElse("localhost"))
      if (request.url.port.nonEmpty) sb.append(s":${request.url.port.get}")
      sb.toString()
    }
    url(basePath, values)
  }

}

/** Constructs [[Endpoint]] values and holds the `outErrors` builder. */
object Endpoint {

  /**
   * Constructs an endpoint for a route pattern. Its input is the path
   * parameters of the route; it has no output, no error type and no
   * authentication yet, and request decoding errors get status 400 Bad Request
   * with a JSON body.
   */
  def apply[Input](
    route: RoutePattern[Input],
  ): Endpoint[Input, Input, ZNothing, ZNothing, AuthType.None] =
    Endpoint(
      route,
      route.toHttpCodec,
      HttpCodec.unused,
      HttpCodec.unused,
      HttpContentCodec.responseErrorCodec,
      Doc.empty,
      AuthType.None,
    )

  @nowarn("msg=type parameter .* defined")
  /**
   * The builder that `Endpoint#outErrors` returns. Its `apply` takes one error
   * codec for each subtype of `Err2` (from two to twenty-two codecs) and
   * returns the endpoint with an error codec that encodes each error with the
   * first codec whose subtype matches the error's runtime class.
   *
   * {{{
   * import zio.http._
   * import zio.http.codec.HttpCodec
   * import zio.schema.Schema
   *
   * sealed trait OrderError
   * final case class UnknownOrder(orderId: Int)    extends OrderError
   * final case class OrderLocked(reason: String) extends OrderError
   *
   * implicit val unknownSchema: Schema[UnknownOrder] = Schema[Int].transform(UnknownOrder(_), _.orderId)
   * implicit val lockedSchema: Schema[OrderLocked]   = Schema[String].transform(OrderLocked(_), _.reason)
   *
   * val cancel: Endpoint[Int, Int, OrderError, Unit, AuthType.None] =
   *   Endpoint(Method.DELETE / "orders" / int("orderId"))
   *     .out[Unit]
   *     .outErrors[OrderError](
   *       HttpCodec.error[UnknownOrder](Status.NotFound),
   *       HttpCodec.error[OrderLocked](Status.Conflict),
   *     )
   * }}}
   */
  final case class OutErrors[PathInput, Input, Err, Output, Auth <: AuthType, Err2](
    self: Endpoint[PathInput, Input, Err, Output, Auth],
  ) extends AnyVal {

    /**
     * Returns the endpoint with an error codec built from these two codecs, one
     * per subtype of `Err2`, placed in front of the endpoint's earlier error
     * codecs.
     */
    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f2(codec1, codec2)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these three codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f3(codec1, codec2, codec3)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these four codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag, Sub4 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f4(codec1, codec2, codec3, codec4)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these five codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f5(codec1, codec2, codec3, codec4, codec5)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these six codecs, one
     * per subtype of `Err2`, placed in front of the endpoint's earlier error
     * codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f6(codec1, codec2, codec3, codec4, codec5, codec6)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these seven codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f7(codec1, codec2, codec3, codec4, codec5, codec6, codec7)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these eight codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f8(codec1, codec2, codec3, codec4, codec5, codec6, codec7, codec8)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    // apply 9
    /**
     * Returns the endpoint with an error codec built from these nine codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f9(codec1, codec2, codec3, codec4, codec5, codec6, codec7, codec8, codec9)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these ten codecs, one
     * per subtype of `Err2`, placed in front of the endpoint's earlier error
     * codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec =
        HttpCodec.enumeration.f10(codec1, codec2, codec3, codec4, codec5, codec6, codec7, codec8, codec9, codec10)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these eleven codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f11(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these twelve codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f12(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these thirteen
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f13(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these fourteen
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f14(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these fifteen codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f15(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these sixteen codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f16(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these seventeen
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f17(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these eighteen
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
      Sub18 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
      codec18: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub18],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f18(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
        codec18,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these nineteen
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
      Sub18 <: Err2: ClassTag,
      Sub19 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
      codec18: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub18],
      codec19: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub19],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f19(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
        codec18,
        codec19,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these twenty codecs,
     * one per subtype of `Err2`, placed in front of the endpoint's earlier
     * error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
      Sub18 <: Err2: ClassTag,
      Sub19 <: Err2: ClassTag,
      Sub20 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
      codec18: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub18],
      codec19: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub19],
      codec20: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub20],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f20(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
        codec18,
        codec19,
        codec20,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these twenty-one
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
      Sub18 <: Err2: ClassTag,
      Sub19 <: Err2: ClassTag,
      Sub20 <: Err2: ClassTag,
      Sub21 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
      codec18: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub18],
      codec19: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub19],
      codec20: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub20],
      codec21: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub21],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f21(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
        codec18,
        codec19,
        codec20,
        codec21,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    /**
     * Returns the endpoint with an error codec built from these twenty-two
     * codecs, one per subtype of `Err2`, placed in front of the endpoint's
     * earlier error codecs.
     */
    def apply[
      Sub1 <: Err2: ClassTag,
      Sub2 <: Err2: ClassTag,
      Sub3 <: Err2: ClassTag,
      Sub4 <: Err2: ClassTag,
      Sub5 <: Err2: ClassTag,
      Sub6 <: Err2: ClassTag,
      Sub7 <: Err2: ClassTag,
      Sub8 <: Err2: ClassTag,
      Sub9 <: Err2: ClassTag,
      Sub10 <: Err2: ClassTag,
      Sub11 <: Err2: ClassTag,
      Sub12 <: Err2: ClassTag,
      Sub13 <: Err2: ClassTag,
      Sub14 <: Err2: ClassTag,
      Sub15 <: Err2: ClassTag,
      Sub16 <: Err2: ClassTag,
      Sub17 <: Err2: ClassTag,
      Sub18 <: Err2: ClassTag,
      Sub19 <: Err2: ClassTag,
      Sub20 <: Err2: ClassTag,
      Sub21 <: Err2: ClassTag,
      Sub22 <: Err2: ClassTag,
    ](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
      codec5: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub5],
      codec6: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub6],
      codec7: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub7],
      codec8: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub8],
      codec9: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub9],
      codec10: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub10],
      codec11: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub11],
      codec12: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub12],
      codec13: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub13],
      codec14: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub14],
      codec15: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub15],
      codec16: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub16],
      codec17: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub17],
      codec18: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub18],
      codec19: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub19],
      codec20: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub20],
      codec21: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub21],
      codec22: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub22],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f22(
        codec1,
        codec2,
        codec3,
        codec4,
        codec5,
        codec6,
        codec7,
        codec8,
        codec9,
        codec10,
        codec11,
        codec12,
        codec13,
        codec14,
        codec15,
        codec16,
        codec17,
        codec18,
        codec19,
        codec20,
        codec21,
        codec22,
      )
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

  }

  private[endpoint] val defaultMediaTypes =
    NonEmptyChunk(MediaTypeWithQFactor(MediaType.application.`json`, Some(1)))
}
