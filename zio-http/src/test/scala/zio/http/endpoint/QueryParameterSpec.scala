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
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.testEndpoint
import zio.http.forms.Fixtures.formField

object QueryParameterSpec extends ZIOHttpSpec {
  def spec = suite("QueryParameterSpec")(
    test("simple request with query parameter") {
      check(Gen.int, Gen.int, Gen.alphaNumericString) { (userId, postId, username) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .implement {
                Handler.fromFunction { userId =>
                  s"path(users, $userId)"
                }
              },
            Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
              .query(query("name"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, postId, name) =>
                  s"path(users, $userId, posts, $postId) query(name=$name)"
                }
              },
          ),
        ) _
        testRoutes(s"/users/$userId", s"path(users, $userId)") &&
        testRoutes(
          s"/users/$userId/posts/$postId?name=$username",
          s"path(users, $userId, posts, $postId) query(name=$username)",
        )
      }
    },
    test("optional query parameter") {
      check(Gen.int, Gen.alphaNumericString) { (userId, details) =>
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
        testRoutes(s"/users/$userId", s"path(users, $userId, None)") &&
        testRoutes(s"/users/$userId?details=", s"path(users, $userId, Some())") &&
        testRoutes(s"/users/$userId?details=$details", s"path(users, $userId, Some($details))")
      }
    },
    test("multiple optional query parameters") {
      check(Gen.int, Gen.alphaNumericString, Gen.alphaNumericString) { (userId, key, value) =>
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
        testRoutes(s"/users/$userId", s"path(users, $userId, None, None)") &&
        testRoutes(s"/users/$userId?key=&value=", s"path(users, $userId, Some(), Some())") &&
        testRoutes(s"/users/$userId?key=&value=$value", s"path(users, $userId, Some(), Some($value))") &&
        testRoutes(s"/users/$userId?key=$key&value=$value", s"path(users, $userId, Some($key), Some($value))")
      }
    },
  )
}
