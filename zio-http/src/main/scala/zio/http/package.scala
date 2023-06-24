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

package zio

package object http extends PathSyntax with RequestSyntax with RouteDecoderModule {

  /**
   * Smartly constructs a handler from the specified value. If you have
   * difficulty using this function, then please use the constructors on
   * [[zio.http.Handler]] directly.
   */
  def handler[H](handler: H)(implicit h: HandlerConstructor[H]): Handler[h.Env, h.Err, h.In, h.Out] =
    Handler.from(handler)

  /**
   * Constructs a route given a route pattern (specified in the first parameter
   * list), and a handler for the route (specified in the second parameter
   * list).
   *
   * If the route pattern produces input, you should use the second parameter
   * list to specify a function that takes the input, and returns a handler.
   */
  def route[PathInput](routePattern: RoutePattern[PathInput]): Route.UnhandledConstructor[PathInput] =
    new Route.UnhandledConstructor(routePattern)

  def routes[Env, Err](route1: Route[Env, Err], routes: Route[Env, Err]*): Routes[Env, Err] =
    Routes(Chunk(route1) ++ Chunk.fromIterable(routes))

  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]

  type HttpAppMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type HandlerAspect[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    HandlerAspect.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type RequestHandlerMiddleware[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] =
    RequestHandlerMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = Env
      type OutErr[Err] = Err
    }

  type HttpApp[-R, +Err] = Http[R, Err, Request, Response]
  type UHttpApp          = HttpApp[Any, Nothing]
  type RHttpApp[-R]      = HttpApp[R, Throwable]
  type EHttpApp          = HttpApp[Any, Throwable]
  type UHttp[-A, +B]     = Http[Any, Nothing, A, B]
  type App[-R]           = HttpApp[R, Response]

  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient

  type SocketApp[-R] = Handler[R, Throwable, WebSocketChannel, Any]

  /**
   * A channel that allows websocket frames to be written to it.
   */
  type WebSocketChannel = Channel[WebSocketChannelEvent, WebSocketChannelEvent]

  /**
   * A channel that allows websocket frames to be read and write to it.
   */
  type WebSocketChannelEvent = ChannelEvent[WebSocketFrame]
}
