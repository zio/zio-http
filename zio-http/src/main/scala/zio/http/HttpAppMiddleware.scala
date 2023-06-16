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

package zio.http

import zio.{Trace, ZIO}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HttpAppMiddleware extends RequestHandlerMiddlewares with HttpRoutesMiddlewares {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
    type OutEnv[Env]
    type OutErr[Err]

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      http: Http[Env, Err, Request, Response],
    )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], Request, Response]
  }

  trait Simple[-UpperEnv, +LowerErr] extends Contextual[Nothing, UpperEnv, LowerErr, Any] {
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err
  }

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate
   */
  def allow: Allow = new Allow(())

  /**
   * Creates a middleware which can allow or disallow access to an http based on
   * the predicate effect
   */
  def allowZIO: AllowZIO = new AllowZIO(())

  /**
   * An empty middleware that doesn't do perform any operations on the provided
   * Http and returns it as it is.
   */
  def identity: HttpAppMiddleware[Nothing, Any, Nothing, Any] =
    new HttpAppMiddleware.Simple[Any, Nothing] {
      override def apply[Env, Err](
        http: Http[Env, Err, Request, Response],
      )(implicit trace: Trace): Http[Env, Err, Request, Response] =
        http
    }

  final class Allow(val unit: Unit) extends AnyVal {
    def apply(condition: Request => Boolean): HttpAppMiddleware[Nothing, Any, Nothing, Any] =
      new HttpAppMiddleware.Simple[Any, Nothing] {
        override def apply[Env, Err](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          http.when(condition)
      }
  }

  final class AllowZIO(val unit: Unit) extends AnyVal {
    def apply(
      condition: Request => ZIO[Any, Nothing, Boolean],
    ): HttpAppMiddleware[Nothing, Any, Nothing, Any] =
      new HttpAppMiddleware.Simple[Any, Nothing] {
        override def apply[Env, Err1](
          http: Http[Env, Err1, Request, Response],
        )(implicit trace: Trace): Http[Env, Err1, Request, Response] =
          http.whenZIO(condition)
      }
  }

  implicit final class HttpAppMiddlewareSyntax[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr](
    val self: HttpAppMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr],
  ) extends AnyVal {

    /**
     * Applies Middleware based only if the condition function evaluates to true
     */
    def when(
      condition: Request => Boolean,
    )(implicit trace: Trace): HttpAppMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] =
      new HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          Http.fromHttp { request =>
            val transformed = if (condition(request)) self(http) else http
            transformed
          }
      }

    /**
     * Applies Middleware based only if the condition effectful function
     * evaluates to true
     */
    def whenZIO(
      condition: Request => ZIO[Any, Nothing, Boolean],
    )(implicit
      trace: Trace,
    ): HttpAppMiddleware[LowerEnv, UpperEnv, LowerErr, UpperErr] =
      new HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
        override type OutEnv[Env] = Env
        override type OutErr[Err] = Err

        override def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[Env, Err, Request, Response] =
          Http.fromHttpZIO { request =>
            condition(request).map { condition =>
              val transformed = if (condition) self(http) else http
              transformed
            }
          }
      }
  }
}
