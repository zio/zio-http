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
import zio.http._
import zio.http.codec.Doc
import zio.http.endpoint.AuthType.None
import zio.json.ast.Json
import zio.test._

object OptionalBodySpec extends ZIOHttpSpec {

  import zio.schema.codec.json._

  private val endpoint: Endpoint[Unit, Option[Json], ZNothing, Json, None] =
    Endpoint(RoutePattern.POST / "optional" / "body")
      .in[Option[Json]](mediaType = MediaType.application.json, doc = Doc.p("Maybe data"))
      .out[Json](mediaType = MediaType.application.json, doc = Doc.p("Result"))

  private val api: Routes[Any, Nothing] =
    endpoint.implementPurely {
      case Some(value) => value
      case scala.None => Json.Obj("no" -> Json.Str("body"))
    }.toRoutes

  private def makeRequest(body: Option[Json]): Request =
    Request
      .post(
        url = URL.root / "optional" / "body",
        body = body.fold(ifEmpty = Body.empty)(b => Body.fromString(b.toString()))
      )
      .addHeader(Header.Accept(MediaType.application.json))

  override def spec: Spec[TestEnvironment with Scope, Throwable] =
    suite("OptionalBodySpec")(
      test("accepts empty body") {
        val body = Option.empty[Json]

        for {
          response <- api.runZIO(makeRequest(body))
          body <- response.body.asString
        } yield assertTrue(body == """{"no":"body"}""")
      },
      test("accepts non-empty body") {
        val body = Some(Json.Obj("key" -> Json.Str("value")))

        for {
          response <- api.runZIO(makeRequest(body))
          body <- response.body.asString
        } yield assertTrue(body == """{"key":"value"}""")
      },
    )

}
