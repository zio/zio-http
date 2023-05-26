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

import zio.http.internal.middlewares.Cors

private[zio] trait HttpRoutesMiddlewares extends Cors {

  def dropTrailingSlash: HttpAppMiddleware[Nothing, Any, Nothing, Any] =
    dropTrailingSlash(onlyIfNoQueryParams = false)

  /**
   * Removes the trailing slash from the path.
   */
  def dropTrailingSlash(onlyIfNoQueryParams: Boolean): HttpAppMiddleware[Nothing, Any, Nothing, Any] =
    new HttpAppMiddleware.Simple[Any, Nothing] {
      override def apply[R1, Err1](
        http: Http[R1, Err1, Request, Response],
      )(implicit trace: Trace): Http[R1, Err1, Request, Response] =
        http.contramap((request: Request) =>
          if (!onlyIfNoQueryParams || request.url.queryParams.isEmpty) request.dropTrailingSlash else request,
        )
    }
}

object HttpRoutesMiddlewares extends HttpRoutesMiddlewares
