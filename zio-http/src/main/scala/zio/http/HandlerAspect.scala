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

import zio.Trace

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HandlerAspect {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
    self =>
    type OutEnv[Env]
    type OutErr[Err]

    def apply[Env >: LowerEnv <: UpperEnv, Err >: LowerErr <: UpperErr](
      handler: Handler[Env, Err, Request, Response],
    )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response]
  }

  trait Simple[-UpperEnv, +LowerErr] extends Contextual[Nothing, UpperEnv, LowerErr, Any] {
    self =>
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err

    def apply[Env <: UpperEnv, Err >: LowerErr](
      handler: Handler[Env, Err, Request, Response],
    )(implicit trace: Trace): Handler[Env, Err, Request, Response]

    final def toMiddleware: RequestHandlerMiddleware.WithOut[Nothing, UpperEnv, LowerErr, Any, OutEnv, OutErr] =
      new RequestHandlerMiddleware.Simple[UpperEnv, LowerErr] {
        override def apply[Env <: UpperEnv, Err >: LowerErr](
          handler: Handler[Env, Err, Request, Response],
        )(implicit trace: Trace): Handler[Env, Err, Request, Response] =
          self(handler)
      }
  }

  def identity[AIn, AOut]: HandlerAspect[Nothing, Any, Nothing, Any] =
    new HandlerAspect.Simple[Any, Nothing] {
      override def apply[Env >: Nothing <: Any, Err >: Nothing <: Any](
        handler: Handler[Env, Err, Request, Response],
      )(implicit trace: Trace): Handler[Env, Err, Request, Response] =
        handler
    }
}
