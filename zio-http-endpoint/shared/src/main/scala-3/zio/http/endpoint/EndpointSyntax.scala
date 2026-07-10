/*
 * Copyright 2026 the ZIO HTTP contributors.
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

import scala.quoted.*
import zio.blocks.combinators.Unions
import zio.blocks.endpoint.{Alternator, AuthType, CodecKind, Endpoint, HttpCodec}
import zio.http.{Client, Halt, Handler, Request, Response, ResultType, Route, Status, URL}
import zio.http.ResultType._

/**
 * User-facing `.call` and `.implement` syntax over a zio-blocks
 * [[zio.blocks.endpoint.Endpoint]].
 *
 * `import zio.http.endpoint.*` alone brings extensions into scope. The result
 * of `.call` is a real Scala 3 union `Err | Output`, produced by zio-blocks'
 * own [[Unions]] machinery via [[Alternator.fromUnions]].
 *
 * Current, tested behavior (see `.omo/notepads/endpoint-blocks/decisions.md`):
 *   - `.implement`: the handler receives the complete `Input` value and returns
 *     `F[Err | Output]`, dispatched across effect types by the
 *     [[EndpointResultHandler]] TC.
 *   - Partial parameter application (handler declaring a SUBSET of Input fields
 *     matched by name+type) and the `.unused` marker's 4-combination warning
 *     logic are NOT implemented on Scala 3: the intended inline macro is
 *     blocked by a quoted type-parameter inference issue (the macro context
 *     cannot infer `Input: Type` when called from an extension method with
 *     implicit type parameters). [[Unused]] exists as a type only, with no
 *     compile-time effect.
 */
extension [PathInput, Input, Err, Output, Auth <: AuthType](
  endpoint: Endpoint[PathInput, Input, Err, Output, Auth]
) {

  /**
   * Turns this endpoint into a [[Route]] backed by a user-provided handler.
   *
   * The handler receives the complete `Input` value and returns
   * `F[Err | Output]`. This single method (no overloads per effect type) is
   * dispatched by the `resultHandler` TC across all effect types `F[_]` (ZIO,
   * IO, Try, Identity, custom monads).
   *
   * Partial application (handler declaring a SUBSET of Input fields matched by
   * name+type) and `.unused` warning emission are NOT implemented on Scala 3:
   * the intended inline macro is blocked by a quoted type-parameter inference
   * issue (see the type-level Scaladoc above and
   * `.omo/notepads/endpoint-blocks/decisions.md`). Today the whole `Input`
   * value is passed to the handler as-is.
   */
  transparent inline def implement[F[_]](inline handler: Any)(using
    resultHandler: EndpointResultHandler[F],
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
  ): Route[Nothing] =
    ${
      EndpointImplementMacro.implementImpl[PathInput, Input, Err, Output, Auth, F](
        'endpoint,
        'handler,
        'resultHandler,
        'unions,
      )
    }

  /**
   * Like [[implement]], but enforces authentication first: the summoned
   * [[EndpointAuthHandler]] validates credentials from the request and extracts
   * a `Session`, which is passed to the handler alongside the decoded `Input`.
   * On authentication failure the endpoint's `auth.unauthorizedStatus` response
   * is returned and the handler is never invoked.
   */
  def implementAuth[Session, F[_]](handler: (Session, Input) => F[Err | Output])(using
    resultHandler: EndpointResultHandler[F],
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
    authHandler: EndpointAuthHandler[Auth, Session],
  ): Route[Any] =
    EndpointBridge.implementAuth(endpoint, handler, resultHandler, Alternator.fromUnions(unions), authHandler)

  /**
   * Invokes this endpoint against `client`, returning the decoded
   * `Err | Output` union.
   *
   * The request is built from `input` via the endpoint's route pattern and
   * input codec; the response is decoded against the error/output codecs and
   * merged into the union using zio-blocks' [[Unions]] machinery.
   */
  def call(client: Client, input: Input)(using
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
  ): Err | Output =
    EndpointBridge.call(endpoint, client, input, Alternator.fromUnions(unions))
}

/**
 * Server- and client-side bridging between a zio-blocks endpoint and the
 * concrete `zio.http` request/response types. All members are
 * `private[endpoint]` — users only see [[implement]] / [[call]].
 */
private[endpoint] object EndpointBridge {

  /** HTTP status used for successful output responses. */
  private val okStatus: Status = Status.Ok

  /** HTTP status used for error responses. */
  private val errorStatus: Status = Status.BadRequest

  /**
   * Encodes an `Err | Output` union into a [[Response]], separating it back
   * into `Either[Err, Output]` via the [[Alternator]] and selecting the
   * matching error/output codec plus status.
   */
  private def encodeResult[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    result: Err | Output,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Response =
    alternator.separate(result) match {
      case Left(err)     => EndpointCodec.encodeResponse(endpoint.error, err, errorStatus)
      case Right(output) => EndpointCodec.encodeResponse(endpoint.output, output, okStatus)
    }

  def encodeResultPublic[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    result: Err | Output,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Response =
    encodeResult(endpoint, result, alternator)

  /**
   * Server-side dispatch: builds a [[Route]] that decodes requests, runs the
   * user's handler, and encodes the result back to a [[Response]].
   */
  def implement[PathInput, Input, Err, Output, Auth <: AuthType, F[_]](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: Input => F[Err | Output],
    resultHandler: EndpointResultHandler[F],
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Route[Any] = {
    val handlerFn: Request => Response | Halt = { request =>
      EndpointCodec.decodeRequest(endpoint.input, request) match {
        case Left(_)      =>
          Response.badRequest
        case Right(input) =>
          val userEffect: F[Err | Output] = handler(input)
          val unionResult: Err | Output   = resultHandler.run(userEffect)
          encodeResult(endpoint, unionResult, alternator)
      }
    }

    val httpHandler: Handler[Any, Any] = Handler(handlerFn)
    Route(endpoint.route, httpHandler)
  }

  def implementAuth[PathInput, Input, Err, Output, Auth <: AuthType, Session, F[_]](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: (Session, Input) => F[Err | Output],
    resultHandler: EndpointResultHandler[F],
    alternator: Alternator.WithOut[Err, Output, Err | Output],
    authHandler: EndpointAuthHandler[Auth, Session],
  ): Route[Any] = {
    val handlerFn: Request => Response | Halt = { request =>
      authHandler.authenticate(request, endpoint.auth) match {
        case Left(unauthorized) => unauthorized
        case Right(session)     =>
          EndpointCodec.decodeRequest(endpoint.input, request) match {
            case Left(_)      => Response.badRequest
            case Right(input) =>
              val unionResult = resultHandler.run(handler(session, input))
              encodeResult(endpoint, unionResult, alternator)
          }
      }
    }

    val httpHandler: Handler[Any, Any] = Handler(handlerFn)
    Route(endpoint.route, httpHandler)
  }

  /**
   * Client-side dispatch: builds a [[Request]] from `input`, sends it, and
   * decodes the response into the `Err | Output` union (error codec first, then
   * output codec) using the [[Alternator]].
   */
  def call[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    client: Client,
    input: Input,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output = {
    val request  = buildRequest(endpoint, input)
    val response = client.send(request)
    decodeResponse(endpoint, response, alternator)
  }

  /**
   * Builds an outgoing [[Request]] from the endpoint's method/path plus input
   * body.
   */
  private def buildRequest[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    input: Input,
  ): Request = {
    val pattern = endpoint.route
    val method  = pattern.method
    val body    = EndpointCodec.encodeRequestBody(endpoint.input, input)
    Request(
      method = method,
      url = URL.root, // TODO: extract path from zio.blocks.endpoint.RoutePattern when API is available
      headers = zio.http.Headers.empty,
      body = body,
      version = zio.http.Version.`HTTP/1.1`,
    )
  }

  /**
   * Decodes a [[Response]] into the union: on a 2xx status the output codec is
   * used, otherwise the error codec, and the value is combined into the union
   * via the [[Alternator]].
   *
   * For simplicity, this PoC checks against `Status.Ok` only; a richer
   * implementation would examine the full 2xx range via status codes.
   */
  private def decodeResponse[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    response: Response,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output =
    if (response.status == Status.Ok)
      EndpointCodec.decodeResponse(endpoint.output, response) match {
        case Right(output) => alternator.combine(Right(output))
        case Left(message) => throw new RuntimeException(s"Failed to decode endpoint output: $message")
      }
    else
      EndpointCodec.decodeResponse(endpoint.error, response) match {
        case Right(err)    => alternator.combine(Left(err))
        case Left(message) => throw new RuntimeException(s"Failed to decode endpoint error: $message")
      }
}
