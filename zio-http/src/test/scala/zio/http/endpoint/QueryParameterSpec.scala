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

import java.time.Instant

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.codec.{DecodeError, JsonCodec}
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema, StandardType}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.testEndpoint
import zio.http.forms.Fixtures.formField

object QueryParameterSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  case class NewPost(value: String)

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
  )

  def spec = suite("QueryParameterSpec")(
    test("optional query parameter") {
      val testRoutes = testEndpoint(
        Routes(
          Endpoint(GET / "users" / int("userId"))
            .query(query("details").optional)
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, details) =>
                s"path(users, $userId, $details)"
              }
            },
        ),
      ) _
      testRoutes("/users/123", "path(users, 123, None)") &&
      testRoutes("/users/123?details=", "path(users, 123, Some())") &&
      testRoutes("/users/123?details=456", "path(users, 123, Some(456))")
    },
    test("multiple optional query parameters") {
      val testRoutes = testEndpoint(
        Routes(
          Endpoint(GET / "users" / int("userId"))
            .query(query("key").optional)
            .query(query("value").optional)
            .out[String]
            .implement {
              Handler.fromFunction { case (userId, key, value) =>
                s"path(users, $userId, $key, $value)"
              }
            },
        ),
      ) _
      testRoutes("/users/123", "path(users, 123, None, None)") &&
      testRoutes("/users/123?key=&value=", "path(users, 123, Some(), Some())") &&
      testRoutes("/users/123?key=&value=X", "path(users, 123, Some(), Some(X))") &&
      testRoutes("/users/123?key=X&value=Y", "path(users, 123, Some(X), Some(Y))")
    },
  )
}
