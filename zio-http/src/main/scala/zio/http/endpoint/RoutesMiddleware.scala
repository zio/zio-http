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

import zio.http._

/**
 * A [[RoutesMiddleware]] defines the middleware implementation for a given
 * [[EndpointMiddleware]]. These middleware are defined by a pair of functions,
 * referred to as _incoming interceptor_ and _outgoing interceptor_, which are
 * applied to incoming requests and outgoing responses.
 */
trait RoutesMiddleware[-Env, State, +M <: EndpointMiddleware] {
  val middleware: M

  final type Err = middleware.Err
  final type In  = middleware.In
  final type Out = middleware.Out

  /**
   * The incoming interceptor is responsible for taking the input to the
   * middleware, derived from the request according to the definition of the
   * middleware, and either failing, or producing a state value, which will be
   * passed with the outgoing interceptor.
   */
  def incoming(input: In): ZIO[Env, Err, State]

  /**
   * The outgoing interceptor is responsible for taking the state value produced
   * by the incoming interceptor, and either failing, or producing an output
   * value, which will be used to patch the response.
   */
  def outgoing(state: State): ZIO[Env, Err, Out]

  /**
   * Converts this [[RoutesMiddleware]] to a [[zio.http.HandlerAspect]], which
   * can be applied in straightforward fashion to any request handler or HTTP.
   */
  final def toHandlerAspect: HandlerAspect.Simple[Env, Nothing] =
    new HandlerAspect.Simple[Env, Nothing] {
      def apply[R1 >: Nothing <: Env, E1 >: Nothing <: Any](handler: Handler[R1, E1, Request, Response])(implicit
        trace: Trace,
      ): Handler[R1, E1, Request, Response] = {
        Handler.fromFunctionZIO[Request] { request =>
          decodeMiddlewareInput(request).flatMap { input =>
            incoming(input).foldZIO(
              e => ZIO.succeed(encodeMiddlewareError(e)),
              { state =>
                handler(request).flatMap { response =>
                  outgoing(state).fold(
                    encodeMiddlewareError(_),
                    { output =>
                      response.patch(encodeMiddlewareOutput(output))
                    },
                  )
                }
              },
            )
          }
        }
      }
    }

  private def decodeMiddlewareInput(request: Request): ZIO[Env, Nothing, In] =
    middleware.input.decodeRequest(request).orDie

  private def encodeMiddlewareOutput(output: Out): Response.Patch =
    middleware.output.encodeResponsePatch(output)

  private def encodeMiddlewareError(error: Err): Response =
    middleware.error.encodeResponse(error)
}
object RoutesMiddleware                                       {

  /**
   * A [[RoutesMiddleware]] that does nothing.
   */
  val none: RoutesMiddleware[Any, Unit, EndpointMiddleware.None] =
    EndpointMiddleware.none.implement(_ => ZIO.unit)(_ => ZIO.unit)

  /**
   * Constructs a new [[RoutesMiddleware]] from both the definition of the
   * middleware, together with a pair of incoming and outgoing interceptors.
   */
  def make[M <: EndpointMiddleware](
    middleware: M,
  ): Apply[M] = new Apply[M](middleware)

  final class Apply[M <: EndpointMiddleware](val m: M) extends AnyVal {
    def apply[Env, State](
      incoming0: m.In => ZIO[Env, m.Err, State],
    )(outgoing0: State => ZIO[Env, m.Err, m.Out]): RoutesMiddleware[Env, State, m.type] =
      new RoutesMiddleware[Env, State, m.type] {
        val middleware: m.type = m

        def incoming(input: In): ZIO[Env, m.Err, State] = incoming0(input)

        def outgoing(state: State): ZIO[Env, m.Err, Out] = outgoing0(state)
      }
  }
}
