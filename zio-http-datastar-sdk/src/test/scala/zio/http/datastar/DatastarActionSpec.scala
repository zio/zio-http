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

package zio.http.datastar

import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object DatastarActionSpec extends ZIOSpecDefault {

  case class User(name: String, email: String)
  object User {
    implicit val schema: Schema[User] = DeriveSchema.gen[User]
  }

  override def spec = suite("DatastarActionSpec")(
    test("GET with no params") {
      val endpoint = Endpoint(RoutePattern.GET / "ping")
      assertTrue(DatastarAction.asString(endpoint) == "@get('/ping')")
    },
    test("GET with path param and query params") {
      val endpoint = Endpoint(RoutePattern.GET / "users" / PathCodec.string("id"))
        .query(HttpCodec.query[String]("status"))
        .query(HttpCodec.query[Int]("limit"))
      assertTrue(
        DatastarAction.asString(endpoint) == "@get('/users/$id?limit=$limit&status=$status')",
      )
    },
    test("POST with path param ignores body") {
      val endpoint = Endpoint(RoutePattern.POST / "users" / PathCodec.int("id"))
        .in[User]
      assertTrue(DatastarAction.asString(endpoint) == "@post('/users/$id')")
    },
    test("DELETE with path param") {
      val endpoint = Endpoint(RoutePattern.DELETE / "users" / PathCodec.int("id"))
      assertTrue(DatastarAction.asString(endpoint) == "@delete('/users/$id')")
    },
    test("Action trait methods") {
      val action = DatastarAction.SimpleAction(Method.PUT, "/items/$itemId")
      assertTrue(
        action.method == Method.PUT,
        action.url == "/items/$itemId",
        action.render == "@put('/items/$itemId')",
      )
    },
  )
}
