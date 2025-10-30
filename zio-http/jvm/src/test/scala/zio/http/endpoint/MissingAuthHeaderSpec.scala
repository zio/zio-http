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

import zio._
import zio.test._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object MissingAuthHeaderSpec extends ZIOHttpSpec {

  def spec = suite("MissingAuthHeaderSpec")(
    test("missing Authorization header should return 401 not 400") {
      val endpoint = Endpoint(Method.GET / "test")
        .header(HeaderCodec.authorization)
        .out[String]

      val routes = endpoint.implementHandler(
        Handler.succeed("success"),
      )

      for {
        response <- routes.toRoutes.runZIO(
          Request.get(url"/test").addHeader(Header.Accept(MediaType.application.`json`)),
        )
        status = response.status
      } yield assertTrue(
        status.code == 401,
        status == Status.Unauthorized,
      )
    },
    test("missing non-auth header should still return 400") {
      val endpoint = Endpoint(Method.GET / "test")
        .header(HeaderCodec.headerAs[String]("X-Custom-Header"))
        .out[String]

      val routes = endpoint.implementHandler(
        Handler.succeed("success"),
      )

      for {
        response <- routes.toRoutes.runZIO(
          Request.get(url"/test").addHeader(Header.Accept(MediaType.application.`json`)),
        )
        status = response.status
      } yield assertTrue(
        status.code == 400,
        status == Status.BadRequest,
      )
    },
  )

}
