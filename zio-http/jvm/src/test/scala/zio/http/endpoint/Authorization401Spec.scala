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

package zio.http.endpoint

import zio.test._

import zio.http._
import zio.http.codec._

object Authorization401Spec extends ZIOSpecDefault {

  override def spec =
    suite("Authorization401Spec")(
      test("should return 401 Unauthorized for missing Authorization header") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]
        val route    = endpoint.implementPurely(_ => ())
        val request  = Request(method = Method.GET, url = url"/test")
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.Unauthorized)
      },
      test("should return 401 Unauthorized for malformed Authorization header") {
        val endpoint = Endpoint(Method.GET / "test")
          .header(HeaderCodec.authorization)
          .out[Unit]
        val route    = endpoint.implementPurely(_ => ())
        val request  =
          Request(method = Method.GET, url = url"/test")
            .addHeader(Header.Custom(Header.Authorization.name, "Basic invalid"))
        // If it fails to decode, it should also be 401.
        for {
          response <- route.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.Unauthorized)
      },
    )
}
