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
import zio.http.{Body, Client, Halt, Handler, Headers, QueryParams, Request, Response, ResultType, Route, Status, URL, Version}
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
   * Invokes this endpoint against `client`, returning the decoded
   * `Err | Output` union.
   *
   * The request is fully rendered from `pathInput` plus `input` via
   * [[EndpointBridge.buildRequest]]: method from the endpoint, path from
   * `RoutePattern.format`, query params, headers (including `Content-Type`),
   * and JSON body bytes. The response is decoded against the error/output
   * codecs and merged into the union using zio-blocks' [[Unions]] machinery.
   */
  def call(client: Client, pathInput: PathInput, input: Input)(using
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
  ): Err | Output =
    EndpointBridge.call(endpoint, client, pathInput, input, Alternator.fromUnions(unions))
}

extension [Input, Err, Output, Auth <: AuthType](
  endpoint: Endpoint[Unit, Input, Err, Output, Auth]
) {

  /**
   * Invokes a root-path (no path params) endpoint against `client`, returning
   * the decoded `Err | Output` union. Shorthand for
   * `call(client, (), input)`.
   */
  def call(client: Client, input: Input)(using
    unions: Unions.Unions.WithOut[Err, Output, Err | Output],
  ): Err | Output =
    EndpointBridge.call(endpoint, client, (), input, Alternator.fromUnions(unions))
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

  /**
   * Client-side dispatch: builds a [[Request]] from `pathInput` plus `input`,
   * sends it, and decodes the response into the `Err | Output` union (error
   * codec first, then output codec) using the [[Alternator]].
   */
  def call[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    client: Client,
    pathInput: PathInput,
    input: Input,
    alternator: Alternator.WithOut[Err, Output, Err | Output],
  ): Err | Output = {
    val request  = buildRequest(endpoint, pathInput, input)
    val response = client.send(request)
    decodeResponse(endpoint, response, alternator)
  }

  /**
   * Builds an outgoing [[Request]] from `EndpointCodecWalker.decompose`
   * output: method from the endpoint, path from `RoutePattern.format` (never
   * `URL.root`), query string from the decomposed query params, headers
   * (including `Content-Type` from the body media type), and JSON body bytes
   * from the codec walk.
   *
   * A [[java.lang.IllegalArgumentException]] naming the offending param is
   * thrown when the input cannot be decomposed (for example a required query,
   * header, body, or path fragment that fails to render) — the request is
   * never silently sent to the root URL.
   */
  def buildRequestPublic[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    pathInput: PathInput,
    input: Input,
  ): Request =
    buildRequest(endpoint, pathInput, input)

  private def buildRequest[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    pathInput: PathInput,
    input: Input,
  ): Request = {
    val decomposed = EndpointCodecWalker.decompose(endpoint, pathInput, input) match {
      case Right(value)  => value
      case Left(message) => throw new IllegalArgumentException(message)
    }
    val baseUrl = URL.fromPath(decomposed.path)
    val url = decomposed.queryParams.foldLeft(baseUrl) { case (acc, (name, value)) =>
      acc.addQueryParams(QueryParams(name -> value))
    }
    val base = Request(
      method = endpoint.route.method,
      url = url,
      headers = Headers.empty,
      body = decomposed.body.getOrElse(Body.empty),
      version = Version.`HTTP/1.1`,
    )
    val withHeaders = decomposed.headers.foldLeft(base) { case (request, (name, value)) =>
      request.addHeader(name, value)
    }
    decomposed.body match {
      case Some(body) => withHeaders.addHeader("Content-Type", body.contentType.mediaType.fullType)
      case None       => withHeaders
    }
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
