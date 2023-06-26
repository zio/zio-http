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
import zio.{Cause, Trace, ZIO}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.unchecked.uncheckedVariance // scalafix:ok;

object RequestHandlerMiddleware {
  type WithOut[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr, OutEnv0[_], OutErr0[_]] =
    Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
      type OutEnv[Env] = OutEnv0[Env]
      type OutErr[Err] = OutErr0[Err]
    }

  trait Contextual[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr]
      extends HandlerAspect.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr]
      with HttpAppMiddleware.Contextual[LowerEnv, UpperEnv, LowerErr, UpperErr] {
    self =>

    final def >>>[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](
      that: RequestHandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
    ): RequestHandlerMiddleware.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeEnv.Out,
      composeErr.Out,
    ] =
      self.andThen(that)(composeEnv, composeErr)

    final def ++[
      LowerEnv2,
      UpperEnv2,
      LowerErr2,
      UpperErr2,
    ](
      that: RequestHandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
    ): RequestHandlerMiddleware.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeEnv.Out,
      composeErr.Out,
    ] =
      self.andThen(that)(composeEnv, composeErr)

    final def andThen[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](
      that: RequestHandlerMiddleware[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2],
    )(implicit
      composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
      composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance,
    ): RequestHandlerMiddleware.WithOut[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper,
      composeEnv.Out,
      composeErr.Out,
    ] =
      new RequestHandlerMiddleware.Contextual[
        composeEnv.Lower,
        composeEnv.Upper,
        composeErr.Lower,
        composeErr.Upper,
      ] {
        override type OutEnv[Env] = composeEnv.Out[Env]
        override type OutErr[Err] = composeErr.Out[Err]

        override def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
          handler: Handler[Env, Err, Request, Response],
        )(implicit trace: Trace): Handler[OutEnv[Env], OutErr[Err], Request, Response] = {
          val h1 =
            self.asInstanceOf[RequestHandlerMiddleware[Nothing, Any, Nothing, Any]].apply(handler)
          val h2 = that
            .asInstanceOf[RequestHandlerMiddleware[Nothing, Any, Nothing, Any]]
            .apply(h1)
          h2.asInstanceOf[Handler[OutEnv[Env], OutErr[Err], Request, Response]]
        }

        override def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
          http: Http[Env, Err, Request, Response],
        )(implicit trace: Trace): Http[OutEnv[Env], OutErr[Err], Request, Response] = {
          val h1 =
            self.asInstanceOf[RequestHandlerMiddleware[Nothing, Any, Nothing, Any]].apply(http)
          val h2 = that
            .asInstanceOf[RequestHandlerMiddleware[Nothing, Any, Nothing, Any]]
            .apply(h1)
          h2.asInstanceOf[Http[OutEnv[Env], OutErr[Err], Request, Response]]
        }
      }
  }

  trait Simple[-UpperEnv, +LowerErr] extends Contextual[Nothing, UpperEnv, LowerErr, Any] {
    self =>
    final type OutEnv[Env] = Env
    final type OutErr[Err] = Err

    def apply[Env <: UpperEnv, Err >: LowerErr](
      handler: Handler[Env, Err, Request, Response],
    )(implicit trace: Trace): Handler[Env, Err, Request, Response]

    override def apply[Env <: UpperEnv, Err >: LowerErr](
      http: Http[Env, Err, Request, Response],
    )(implicit trace: Trace): Http[Env, Err, Request, Response] =
      http match {
        case Http.Empty(errorHandler)                        =>
          Http.Empty(errorHandler)
        case Http.Static(handler, errorHandler)              =>
          Http.Static(apply(handler), errorHandler)
        case route: Http.Router[Env, Err, Request, Response] =>
          Http.fromHttpZIO[Request](route.run(_).map(self(_)))
      }
  }

  def identity: RequestHandlerMiddleware[Nothing, Any, Nothing, Any] =
    new RequestHandlerMiddleware.Simple[Any, Nothing] {
      override def apply[R, E](handler: Handler[R, E, Request, Response])(implicit
        trace: Trace,
      ): Handler[R, E, Request, Response] =
        handler
    }

  implicit final class SimpleSyntax[R, Err](
    val self: RequestHandlerMiddleware[Nothing, R, Err, Any],
  ) extends AnyVal {
    def when(
      condition: Request => Boolean,
    )(implicit
      trace: Trace,
    ): RequestHandlerMiddleware[Nothing, R, Err, Any] =
      new RequestHandlerMiddleware.Simple[R, Err] {
        override def apply[R1 <: R, Err1 >: Err](handler: Handler[R1, Err1, Request, Response])(implicit
          trace: Trace,
        ): Handler[R1, Err1, Request, Response] =
          Handler.fromFunctionHandler[Request].apply[R1, Err1, Response] { in =>
            if (condition(in)) {
              val a = self.apply(handler)
              a
            } else handler
          }
      }

    def whenZIO[R1 <: R, Err1 >: Err](
      condition: Request => ZIO[R1, Err1, Boolean],
    )(implicit trace: Trace): RequestHandlerMiddleware[Nothing, R1, Err1, Any] =
      new RequestHandlerMiddleware.Simple[R1, Err1] {
        override def apply[R2 <: R1, Err2 >: Err1](handler: Handler[R2, Err2, Request, Response])(implicit
          trace: Trace,
        ): Handler[R2, Err2, Request, Response] =
          Handler
            .fromFunctionZIO[Request] { in =>
              condition(in).map {
                case true  => self(handler)
                case false => handler
              }
            }
            .flatten
      }
  }
}
