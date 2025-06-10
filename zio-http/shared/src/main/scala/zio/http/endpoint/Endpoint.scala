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

import zio._
import zio.http.Header.Accept.MediaTypeWithQFactor
import zio.http._
import zio.http.codec._
import zio.http.endpoint.Endpoint.{OutErrors, defaultMediaTypes}
import zio.schema.Schema
import zio.stream.ZStream

import scala.annotation.nowarn
import scala.reflect.ClassTag

/**
 * An [[zio.http.endpoint.Endpoint]] represents an API endpoint for the HTTP
 * protocol. Every `API` has an input, which comes from a combination of the
 * HTTP route, query string parameters, and headers, and an output, which is the
 * data computed by the handler of the API.
 *
 * MiddlewareInput : Example: A subset of `HttpCodec[Input]` that doesn't give
 * access to `Input` MiddlewareOutput: Example: A subset of `Out[Output]` that
 * doesn't give access to `Output` Input: Example: Int Output: Example: User
 *
 * As [[zio.http.endpoint.Endpoint]] is a purely declarative encoding of an
 * endpoint, it is possible to use this model to generate a [[zio.http.Route]]
 * (by supplying a handler for the endpoint), to generate OpenAPI documentation,
 * to generate a type-safe Scala client for the endpoint, and possibly, to
 * generate client libraries in other programming languages.
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
) { self =>

  val authCombiner: Combiner[Input, authType.ClientRequirement]                   =
    implicitly[Combiner[Input, authType.ClientRequirement]]
  val authCodec: HttpCodec[HttpCodecType.RequestType, authType.ClientRequirement] =
    authType.codec

  private[http] def authedInput(implicit
    combiner: Combiner[Input, authType.ClientRequirement],
  ): HttpCodec[HttpCodecType.RequestType, AuthedInput] = {
    input ++ authCodec
  }.asInstanceOf[HttpCodec[HttpCodecType.RequestType, AuthedInput]]

  type AuthedInput = authCombiner.Out

  /**
   * Returns a new API that is derived from this one, but which includes
   * additional documentation that will be included in OpenAPI generation.
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

  def apply(input: Input): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, input)

  def apply[A, B](a: A, b: B)(implicit
    ev: (A, B) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, ev((a, b)))

  def apply[A, B, C](a: A, b: B, c: C)(implicit
    ev: (A, B, C) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(self, ev((a, b, c)))

  def apply[A, B, C, D](a: A, b: B, c: C, d: D)(implicit
    ev: (A, B, C, D) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d)),
    )

  def apply[A, B, C, D, E](a: A, b: B, c: C, d: D, e: E)(implicit
    ev: (A, B, C, D, E) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e)),
    )

  def apply[A, B, C, D, E, F](a: A, b: B, c: C, d: D, e: E, f: F)(implicit
    ev: (A, B, C, D, E, F) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f)),
    )

  def apply[A, B, C, D, E, F, G](a: A, b: B, c: C, d: D, e: E, f: F, g: G)(implicit
    ev: (A, B, C, D, E, F, G) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g)),
    )

  def apply[A, B, C, D, E, F, G, H](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H)(implicit
    ev: (A, B, C, D, E, F, G, H) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h)),
    )

  def apply[A, B, C, D, E, F, G, H, I](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I)(implicit
    ev: (A, B, C, D, E, F, G, H, I) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i)),
    )

  def apply[A, B, C, D, E, F, G, H, I, J](a: A, b: B, c: C, d: D, e: E, f: F, g: G, h: H, i: I, j: J)(implicit
    ev: (A, B, C, D, E, F, G, H, I, J) <:< Input,
  ): Invocation[PathInput, Input, Err, Output, Auth] =
    Invocation(
      self,
      ev((a, b, c, d, e, f, g, h, i, j)),
    )

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

  def auth[Auth0 <: AuthType](auth: Auth0): Endpoint[PathInput, Input, Err, Output, Auth0] =
    copy(authType = auth)

  def scopes: List[String] = authScopesRecursive(authType)

  private def authScopesRecursive(authType: AuthType): List[String] = authType match {
    case AuthType.ScopedAuth(nestedAuth, _) =>
      authType.asInstanceOf[AuthType.ScopedAuth[_]].scopes ++ authScopesRecursive(nestedAuth)
    case AuthType.Or(auth1, auth2, _)       =>
      authScopesRecursive(auth1) ++ authScopesRecursive(auth2)
    case _                                  =>
      Nil
  }

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
   * Hides any details of codec errors from the user.
   */
  def emptyErrorResponse: Endpoint[PathInput, Input, Err, Output, Auth] =
    self.copy(codecError =
      StatusCodec.BadRequest
        .transformOrFail[HttpCodecError](_ => Right(HttpCodecError.CustomError("Empty", "empty")))(_ => Right(())),
    )

  def examplesIn(examples: (String, Input)*): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(input = self.input.examples(examples))

  def examplesIn: Map[String, Input] = self.input.examples

  def examplesOut(examples: (String, Output)*): Endpoint[PathInput, Input, Err, Output, Auth] =
    copy(output = self.output.examples(examples))

  def examplesOut: Map[String, Output] = self.output.examples

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ codec)

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Input, A]): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    header(HeaderCodec.headerAs[A](name))

  /**
   * Returns a new endpoint that requires the specified headers to be present.
   */
  def header[A](implicit
    schema: Schema[A],
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    header(HeaderCodec.headers[A])

  def implement[Env](f: Input => ZIO[Env, Err, Output])(implicit
    trace: Trace,
  ): Route[Env, Nothing] =
    implementHandler(Handler.fromFunctionZIO(f))

  def implementEither(f: Input => Either[Err, Output])(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fromFunctionEither[Input](f))

  def implementPurely(f: Input => Output)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fromFunctionExit[Input](in => Exit.succeed(f(in))))

  def implementAs(output: Output)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.succeed(output))

  def implementAsZIO[Env](output: ZIO[Env, Err, Output])(implicit
    trace: Trace,
  ): Route[Env, Nothing] =
    implementHandler(Handler.fromZIO(output))

  def implementAsError(err: Err)(implicit
    trace: Trace,
  ): Route[Any, Nothing] =
    implementHandler[Any](Handler.fail(err))

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
    }

    val maybeUnauthedResponse = authType.asInstanceOf[AuthType] match {
      case AuthType.None => None
      case _             => Some(Handler.succeed(Response.unauthorized))
    }

    def handlers(config: CodecConfig): Chunk[(Handler[Env, Nothing, Request, Response], HttpCodec.Fallback.Condition)] =
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
                success = output => Exit.succeed(endpoint.output.encodeResponse(output, outputMediaTypes, config)),
                failure = error => Exit.succeed(endpoint.error.encodeResponse(error, outputMediaTypes, config)),
              )
          }
        } -> condition
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
              case Some(HttpCodecError.MissingHeaders(headerNames)) if (headerNames.contains(Header.Authorization.name)) =>
                Handler.succeed(Response.unauthorized)
              case Some(HttpCodecError.MissingHeader(headerName)) if headerName == Header.Authorization.name =>
                Handler.succeed(Response.unauthorized)
              case Some(error)                                     =>
                Handler.fromFunctionZIO { (request: zio.http.Request) =>
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
              case None                                            =>
                Handler.failCause(cause)
            }
          }
      }

    Route.handledIgnoreParams(self.route)(handler)
  }

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema.
   */
  def in[Input2: HttpContentCodec](implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2])

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2] ?? doc)

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2](name))

  /**
   * Returns a new endpoint derived from this one, whose request content must
   * satisfy the specified schema and is documented.
   */
  def in[Input2: HttpContentCodec](name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content[Input2](name) ?? doc))

  def in[Input2: HttpContentCodec](mediaType: MediaType)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content[Input2](mediaType))

  def in[Input2: HttpContentCodec](mediaType: MediaType, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content(mediaType) ?? doc))

  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ HttpCodec.content(name, mediaType))

  def in[Input2: HttpContentCodec](mediaType: MediaType, name: String, doc: Doc)(implicit
    combiner: Combiner[Input, Input2],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = input ++ (HttpCodec.content(name, mediaType) ?? doc))

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
   * of the specified typ.
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
   * specified type for the ok status code.
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
   * specified type for the ok status code and is documented.
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
   * specified type for the specified status code and is documented.
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
   * specified type for the specified status code.
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
   * Converts a codec error into a specific error type. The given media types
   * are sorted by q-factor. Beginning with the highest q-factor.
   */
  def outCodecError(
    codec: HttpCodec[HttpCodecType.ResponseType, HttpCodecError],
  ): Endpoint[PathInput, Input, Err, Output, Auth] =
    self.copy(codecError = codec | self.codecError)

  /**
   * Returns a new endpoint that can fail with the specified error type for the
   * specified status code.
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

  def outErrors[Err2]: OutErrors[PathInput, Input, Err, Output, Auth, Err2] = OutErrors(self)

  def outHeader[A](codec: HeaderCodec[A])(implicit
    combiner: Combiner[Output, A],
  ): Endpoint[PathInput, Input, Err, combiner.Out, Auth] =
    copy(output = self.output ++ codec)

  /**
   * Returns a new endpoint that requires the specified headers to be part of
   * the response.
   */
  def outHeader[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Output, A]): Endpoint[PathInput, Input, Err, combiner.Out, Auth] =
    outHeader(HeaderCodec.headerAs[A](name))

  /**
   * Returns a new endpoint that requires the specified headers to be part of
   * the response.
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

  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    outStream(Status.Ok, mediaType)

  def outStream[Output2: HttpContentCodec](
    mediaType: MediaType,
    doc: Doc,
  )(implicit
    alt: Alternator[ZStream[Any, Nothing, Output2], Output],
  ): Endpoint[PathInput, Input, Err, alt.Out, Auth] =
    outStream(Status.Ok, mediaType, doc)

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
   * Returns a new endpoint that requires the specified query.
   */
  def query[A](codec: QueryCodec[A])(implicit
    combiner: Combiner[Input, A],
  ): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ codec)

  /**
   * Returns a new endpoint that requires the specified query.
   */
  def query[A](
    name: String,
  )(implicit schema: Schema[A], combiner: Combiner[Input, A]): Endpoint[PathInput, combiner.Out, Err, Output, Auth] =
    copy(input = self.input ++ QueryCodec.query[A](name))

  /**
   * Returns a new endpoint that requires the specified query.
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
   * Transforms the input of this endpoint using the specified functions. This
   * is useful to build from different http inputs a domain specific input.
   *
   * For example
   * {{{
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
}

object Endpoint {

  /**
   * Constructs an endpoint for a route pattern.
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
  final case class OutErrors[PathInput, Input, Err, Output, Auth <: AuthType, Err2](
    self: Endpoint[PathInput, Input, Err, Output, Auth],
  ) extends AnyVal {

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f2(codec1, codec2)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f3(codec1, codec2, codec3)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

    def apply[Sub1 <: Err2: ClassTag, Sub2 <: Err2: ClassTag, Sub3 <: Err2: ClassTag, Sub4 <: Err2: ClassTag](
      codec1: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub1],
      codec2: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub2],
      codec3: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub3],
      codec4: HttpCodec[HttpCodecType.Status & HttpCodecType.Content, Sub4],
    )(implicit alt: Alternator[Err2, Err]): Endpoint[PathInput, Input, alt.Out, Output, Auth] = {
      val codec = HttpCodec.enumeration.f4(codec1, codec2, codec3, codec4)
      self.copy[PathInput, Input, alt.Out, Output, Auth](error = codec | self.error)
    }

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
