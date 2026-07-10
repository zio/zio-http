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

import zio.blocks.context.Context
import zio.blocks.endpoint.{AuthType, Endpoint}
import zio.blocks.scope.Scope
import zio.http.{Halt, Handler, Request, Response, Route}
import zio.http.ResultType._

/**
 * Public server-side dispatch entry point emitted by `.implement`'s Scala 2
 * macro. It exists because a blackbox/whitebox macro expands into the CALLER's
 * scope (possibly an external package), so the generated code may only
 * reference public members; this object provides the one public seam and
 * delegates to the `private[endpoint]` codec internals and the `private[http]`
 * [[Handler]] constructors from inside this package.
 *
 * The user handler is already normalized by the macro into
 * `Input => Either[Err, Output]` (the internal union representation); users
 * never write `Left`/`Right` themselves.
 */
object EndpointServer {

  def implement[PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: Input => Either[Err, Output],
  ): Route[Any] = {
    val handlerFn: Request => Response | Halt = { request =>
      EndpointCodec.decodeRequest(endpoint.input, request) match {
        case Right(input) =>
          handler(input) match {
            case Left(err)  => EndpointCodec.encodeResponse(endpoint.error, err, 400)
            case Right(out) => EndpointCodec.encodeResponse(endpoint.output, out, 200)
          }
        case Left(_)      =>
          Response.badRequest
      }
    }
    Route(endpoint.route, Handler(handlerFn))
  }

  /**
   * Public seam emitted by `.implement`'s whitebox macro when the handler
   * declares context parameters (any nominal-typed parameter that is not the
   * `Input`, `Request`, or `Scope`).
   *
   * The macro has already classified the handler's parameters and produced a
   * `run` function that, given the decoded `Input`, the `Request`, the
   * `Context[Ctx]`, and the `Scope`, resolves every argument (decoding the
   * `Input`, injecting `Request`/`Scope`, and pulling each context requirement
   * out of the `Context`) and invokes the user handler, returning the internal
   * `Either[Err, Output]` union representation.
   *
   * This method carries the precise `Ctx` in its result type, so a whitebox
   * expansion propagates `Route[Ctx]` to the call site — `@@`-applying a
   * `Middleware` later discharges the requirement. The generated code cannot
   * build the `Handler`/`Route` itself because [[Handler.extracted]] is
   * `private[http]`; routing through this public method keeps the caller's
   * expansion free of any non-public reference.
   */
  def implementCtx[Ctx, PathInput, Input, Err, Output, Auth <: AuthType](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    run: (Input, Request, Context[Ctx], Scope) => Either[Err, Output],
  ): Route[Ctx] = {
    val httpHandler: Handler[Ctx, Any] =
      Handler.extracted[Ctx, Any] { (request, context, _, scope) =>
        EndpointCodec.decodeRequest(endpoint.input, request) match {
          case Right(input) =>
            run(input, request, context, scope) match {
              case Left(err)  => EndpointCodec.encodeResponse(endpoint.error, err, 400)
              case Right(out) => EndpointCodec.encodeResponse(endpoint.output, out, 200)
            }
          case Left(_)      =>
            Response.badRequest
        }
      }
    Route(endpoint.route, httpHandler)
  }

  def implementAuth[PathInput, Input, Err, Output, Auth <: AuthType, Session](
    endpoint: Endpoint[PathInput, Input, Err, Output, Auth],
    handler: (Session, Input) => Either[Err, Output],
    authHandler: EndpointAuthHandler[Auth, Session],
  ): Route[Any] = {
    val handlerFn: Request => Response | Halt = { request =>
      authHandler.authenticate(request, endpoint.auth) match {
        case Left(unauthorized) => unauthorized
        case Right(session)     =>
          EndpointCodec.decodeRequest(endpoint.input, request) match {
            case Right(input) =>
              handler(session, input) match {
                case Left(err)  => EndpointCodec.encodeResponse(endpoint.error, err, 400)
                case Right(out) => EndpointCodec.encodeResponse(endpoint.output, out, 200)
              }
            case Left(_)      =>
              Response.badRequest
          }
      }
    }
    Route(endpoint.route, Handler(handlerFn))
  }
}
