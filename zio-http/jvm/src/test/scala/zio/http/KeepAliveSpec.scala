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

import zio._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test.{Spec, assert}

import zio.http.internal.{DynamicServer, HttpRunnableSpec, serverTestLayer}

object KeepAliveSpec extends HttpRunnableSpec {

  private val routes                = Handler.ok.toRoutes
  private val connectionCloseHeader = Headers(Header.Connection.Close)
  private val keepAliveHeader       = Headers(Header.Connection.KeepAlive)
  private val appKeepAliveEnabled   = serve

  private def keepAliveSpec = suite("KeepAlive")(
    suite("Http 1.1")(
      test("without connection close") {
        for {
          _   <- appKeepAliveEnabled
          res <- routes.deploy(Request()).map(_.header(Header.Connection))
        } yield assert(res)(isNone)
      },
      test("with connection close") {
        for {
          _   <- appKeepAliveEnabled
          res <- routes.deploy(Request(headers = connectionCloseHeader)).map(_.header(Header.Connection))
        } yield assert(res)(isSome(equalTo(Header.Connection.Close)))
      },
    ),
    suite("Http 1.0")(
      test("without keep-alive") {
        for {
          _   <- appKeepAliveEnabled
          res <- routes.deploy(Request(version = Version.`HTTP/1.0`)).map(_.header(Header.Connection))
        } yield assert(res)(isSome(equalTo(Header.Connection.Close)))
      },
      test("with keep-alive") {
        for {
          _   <- appKeepAliveEnabled
          res <- routes
            .deploy(Request(version = Version.Http_1_0, headers = keepAliveHeader))
            .map(_.header(Header.Connection))
        } yield assert(res)(isNone)
      },
    ),
  )

  override def spec: Spec[Any, Throwable] = {
    suite("KeepAliveSpec") {
      keepAliveSpec
    }
      .provideSome[DynamicServer & Server & Client](Scope.default)
      .provide(DynamicServer.live, serverTestLayer, Client.default) @@ withLiveClock @@ sequential
  }

}
