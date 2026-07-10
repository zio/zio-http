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
package zio.http

import zio.blocks.context.IsNominalType
import zio.http.ResultType.responseAsResult

trait Middleware[UpperCtx, Ctx] { self =>
  def apply(routes: Routes[Ctx]): Routes[UpperCtx]

  def andThen[UpperCtx2](that: Middleware[UpperCtx2, UpperCtx]): Middleware[UpperCtx2, Ctx] =
    new Middleware[UpperCtx2, Ctx] {
      def apply(routes: Routes[Ctx]): Routes[UpperCtx2] =
        that(self(routes))
    }
}

object Middleware {
  def identity[Ctx]: Middleware[Ctx, Ctx] = new Middleware[Ctx, Ctx] {
    def apply(routes: Routes[Ctx]): Routes[Ctx] = routes
  }

  def customAuth[Session](
    validate: Request => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    new Middleware[Any, Session] {
      def apply(routes: Routes[Session]): Routes[Any] =
        Routes.fromIterable(routes.routes.map(secure))

      private def secure(route: Route[Session]): Route[Any] = {
        val wrapped = Handler.extracted[Any, Any] { (request, context, vars, scope) =>
          validate(request) match {
            case Left(response) => responseAsResult(response)
            case Right(session) =>
              route.handler.handle(request, context.add[Session](session), vars, scope)
          }
        }
        Route(route.pattern, wrapped)
      }
    }

  def basicAuth[Session](
    validate: Header.Authorization.Basic => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(basic: Header.Authorization.Basic) => validate(basic)
        case _                                       => Left(Response.unauthorized)
      }
    }

  def bearerAuth[Session](
    validate: Header.Authorization.Bearer => Either[Response, Session],
  )(implicit ev: IsNominalType[Session]): Middleware[Any, Session] =
    customAuth { request =>
      request.header(Header.Authorization) match {
        case Some(bearer: Header.Authorization.Bearer) => validate(bearer)
        case _                                         => Left(Response.unauthorized)
      }
    }
}
