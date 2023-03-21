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

package zio.http.internal

import zio.http._
import zio.http.model._

trait HttpAppTestExtensions {
  implicit class HttpAppSyntax[R, E](route: HttpApp[R, E]) {
    def rawHeader(name: String): Http[R, E, Request, Option[String]] =
      route.map(res => res.rawHeader(name))

    def headerValues: Http[R, E, Request, List[String]] =
      route.map(res => res.headers.toList.map(_.renderedValue.toString))

    def headers: Http[R, E, Request, Headers] =
      route.map(res => res.headers)

    def status: Http[R, E, Request, Status] =
      route.map(res => res.status)
  }

  implicit class RequestHandlerSyntax[R, E](handler: RequestHandler[R, E]) {
    def rawHeader(name: String): Handler[R, E, Request, Option[String]] =
      handler.map(res => res.rawHeader(name))

    def headerValues: Handler[R, E, Request, List[String]] =
      handler.map(res => res.headers.toList.map(_.renderedValue.toString))

    def headers: Handler[R, E, Request, Headers] =
      handler.map(res => res.headers)

    def status: Handler[R, E, Request, Status] =
      handler.map(res => res.status)
  }
}
