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

import zio.blocks.context.Context
import zio.blocks.scope.Scope
import zio.http.ResultType._

sealed trait Handler[-Ctx, -Vars] { self =>

  def handle(
    request: Request,
    context: Context[Ctx],
    vars: Vars,
    scope: Scope,
  ): Response | Halt
}

object Handler {

  def apply[H](h: H)(implicit toHandler: ToHandler[H]): Handler[toHandler.Ctx, toHandler.Vars] =
    toHandler.toHandler(h)

  def handler[H](h: H)(implicit toHandler: ToHandler[H]): Handler[toHandler.Ctx, toHandler.Vars] =
    HandlerMacros.handler(h)

  def succeed(response: Response): Handler[Any, Any] =
    Succeed(response)

  private[http] def fromRequest(f: Request => Response | Halt): Handler[Any, Any] =
    FromRequest(f)

  private[http] def fromRequestCtx[Ctx](f: (Request, Context[Ctx]) => Response | Halt): Handler[Ctx, Any] =
    FromRequestCtx(f)

  private[http] def extracted[Ctx, Vars](
    f: (Request, Context[Ctx], Vars, Scope) => Response | Halt,
  ): Handler[Ctx, Vars] =
    Extracted(f)

  private[http] final case class Succeed(response: Response) extends Handler[Any, Any] {
    override def handle(
      request: Request,
      context: Context[Any],
      vars: Any,
      scope: Scope,
    ): Response | Halt =
      responseAsResult(response)
  }

  private[http] final case class FromRequest(f: Request => Response | Halt) extends Handler[Any, Any] {
    override def handle(
      request: Request,
      context: Context[Any],
      vars: Any,
      scope: Scope,
    ): Response | Halt =
      f(request)
  }

  private[http] final case class FromRequestCtx[Ctx](f: (Request, Context[Ctx]) => Response | Halt)
      extends Handler[Ctx, Any] {
    override def handle(
      request: Request,
      context: Context[Ctx],
      vars: Any,
      scope: Scope,
    ): Response | Halt =
      f(request, context)
  }

  private[http] final case class Extracted[Ctx, Vars](
    f: (Request, Context[Ctx], Vars, Scope) => Response | Halt,
  ) extends Handler[Ctx, Vars] {
    override def handle(
      request: Request,
      context: Context[Ctx],
      vars: Vars,
      scope: Scope,
    ): Response | Halt =
      f(request, context, vars, scope)
  }
}
