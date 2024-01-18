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
import zio.http.codec.HttpCodec.{query, queryInt, queryMultiValue, queryMultiValueBool}
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
    test("query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString)) { (userId, keys) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryMultiValue("key"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, keys) =>
                  s"""path(users, $userId, ${keys.mkString(", ")})"""
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}",
          s"path(users, $userId, ${keys(0)}, ${keys(1)}, ${keys(2)})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}",
          s"path(users, $userId, ${keys(0)}, ${keys(1)})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}",
          s"path(users, $userId, ${keys(0)})",
        )
      }
    },
    test("optional query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString)) { (userId, keys) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryMultiValue("key").optional)
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, keys) =>
                  s"""path(users, $userId, $keys)"""
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}",
          s"path(users, $userId, Some(${NonEmptyChunk.fromIterable(keys.head, keys.tail)}))",
        ) &&
        testRoutes(
          s"/users/$userId",
          s"path(users, $userId, None)",
        ) &&
        testRoutes(
          s"/users/$userId?key=",
          s"path(users, $userId, Some(NonEmptyChunk()))",
        )
      }
    },
    test("multiple query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.alphaNumericString)) {
        (userId, keys, values) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .query(queryMultiValue("key") & queryMultiValue("value"))
                .out[String]
                .implement {
                  Handler.fromFunction { case (userId, keys, values) =>
                    s"""path(users, $userId, $keys, $values)"""
                  }
                },
            ),
          ) _

          testRoutes(
            s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}&value=${values(0)}&value=${values(1)}",
            s"path(users, $userId, NonEmptyChunk(${keys(0)}, ${keys(1)}, ${keys(2)}), NonEmptyChunk(${values(0)}, ${values(1)}))",
          ) &&
          testRoutes(
            s"/users/$userId?key=${keys(0)}&key=${keys(1)}&value=${values(0)}",
            s"path(users, $userId, NonEmptyChunk(${keys(0)}, ${keys(1)}), NonEmptyChunk(${values(0)}))",
          )
      }
    },
    test("mix of multi value and single value query parameters") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.alphaNumericString) { (userId, multi, single) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryMultiValue("multi") & query("single"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, multi, single) =>
                  s"""path(users, $userId, $multi, $single)"""
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?multi=${multi(0)}&multi=${multi(1)}&single=$single",
          s"path(users, $userId, NonEmptyChunk(${multi(0)}, ${multi(1)}), $single)",
        )
      }
    },
    test("either of two multi value query parameters") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.boolean)) { (userId, left, right) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryMultiValue("left") | queryMultiValueBool("right"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, eitherOfParameters) =>
                  s"path(users, $userId, $eitherOfParameters)"
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(NonEmptyChunk(${left(0)}, ${left(1)})))",
        ) &&
        testRoutes(
          s"/users/$userId?right=${right(0)}&right=${right(1)}",
          s"path(users, $userId, Right(NonEmptyChunk(${right(0)}, ${right(1)})))",
        ) &&
        testRoutes(
          s"/users/$userId?right=${right(0)}&right=${right(1)}&left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(NonEmptyChunk(${left(0)}, ${left(1)})))",
        )
      }
    },
    test("either of two multi value query parameters of the same type") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.alphaNumericString)) {
        (userId, left, right) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .query(queryMultiValue("left") | queryMultiValue("right"))
                .out[String]
                .implement {
                  Handler.fromFunction { case (userId, queryParams) =>
                    s"path(users, $userId, $queryParams)"
                  }
                },
            ),
          ) _

          testRoutes(
            s"/users/$userId?left=${left(0)}&left=${left(1)}",
            s"path(users, $userId, NonEmptyChunk(${left(0)}, ${left(1)}))",
          ) &&
          testRoutes(
            s"/users/$userId?right=${right(0)}&right=${right(1)}",
            s"path(users, $userId, NonEmptyChunk(${right(0)}, ${right(1)}))",
          ) &&
          testRoutes(
            s"/users/$userId?right=${right(0)}&right=${right(1)}&left=${left(0)}&left=${left(1)}",
            s"path(users, $userId, NonEmptyChunk(${left(0)}, ${left(1)}))",
          )
      }
    },
    test("either of multi value or single value query parameter") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.alphaNumericString) { (userId, left, right) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryMultiValue("left") | query("right"))
              .out[String]
              .implement {
                Handler.fromFunction { case (userId, queryParams) =>
                  s"path(users, $userId, $queryParams)"
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(NonEmptyChunk(${left(0)}, ${left(1)})))",
        ) &&
        testRoutes(
          s"/users/$userId?right=$right",
          s"path(users, $userId, Right($right))",
        ) &&
        testRoutes(
          s"/users/$userId?right=$right&left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(NonEmptyChunk(${left(0)}, ${left(1)})))",
        )
      }
    },
  )
}
