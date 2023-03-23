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

import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertZIO
import zio.{Scope, durationInt}

import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model._

object KeepAliveSpec extends HttpRunnableSpec {

  val app                         = Handler.ok.toHttp
  val connectionCloseHeader       = Headers(Header.Connection.Close)
  val keepAliveHeader             = Headers(Header.Connection.KeepAlive)
  private val appKeepAliveEnabled = serve(DynamicServer.app)

  def keepAliveSpec = suite("KeepAlive")(
    suite("Http 1.1")(
      test("without connection close") {
        val res = app.deploy.header(Header.Connection).run()
        assertZIO(res)(isNone)
      },
      test("with connection close") {
        val res = app.deploy.header(Header.Connection).run(headers = connectionCloseHeader)
        assertZIO(res)(isSome(equalTo(Header.Connection.Close)))
      },
    ),
    suite("Http 1.0")(
      test("without keep-alive") {
        val res = app.deploy.header(Header.Connection).run(version = Version.Http_1_0)
        assertZIO(res)(isSome(equalTo(Header.Connection.Close)))
      },
      test("with keep-alive") {
        val res = app.deploy
          .header(Header.Connection)
          .run(version = Version.Http_1_0, headers = keepAliveHeader)
        assertZIO(res)(isNone)
      },
    ),
  )

  override def spec = {
    suite("KeepAliveSpec") {
      appKeepAliveEnabled.as(List(keepAliveSpec))
    }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@ timeout(
      30.seconds,
    )
  }

}
