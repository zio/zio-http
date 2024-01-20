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

import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryAll, queryAllBool, queryAllInt, queryInt}
import zio.http.endpoint.EndpointSpec.testEndpoint

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
              .query(queryAll("key"))
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
              .query(queryAll("key").optional)
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
          s"path(users, $userId, Some(${Chunk.fromIterable(keys)}))",
        ) &&
        testRoutes(
          s"/users/$userId",
          s"path(users, $userId, None)",
        ) &&
        testRoutes(
          s"/users/$userId?key=",
          s"path(users, $userId, Some(${Chunk.empty}))",
        )
      }
    },
    test("multiple query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.alphaNumericString)) {
        (userId, keys, values) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .query(queryAll("key") & queryAll("value"))
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
            s"path(users, $userId, ${Chunk.fromIterable(keys)}, ${Chunk.fromIterable(values)})",
          ) &&
          testRoutes(
            s"/users/$userId?key=${keys(0)}&key=${keys(1)}&value=${values(0)}",
            s"path(users, $userId, ${Chunk(keys(0), keys(1))}, ${Chunk(values(0))})",
          )
      }
    },
    test("mix of multi value and single value query parameters") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.alphaNumericString) { (userId, multi, single) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryAll("multi") & query("single"))
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
          s"path(users, $userId, ${Chunk.fromIterable(multi)}, $single)",
        )
      }
    },
    test("either of two multi value query parameters") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.boolean)) { (userId, left, right) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryAll("left") | queryAllBool("right"))
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
          s"path(users, $userId, Left(${Chunk.fromIterable(left)}))",
        ) &&
        testRoutes(
          s"/users/$userId?right=${right(0)}&right=${right(1)}",
          s"path(users, $userId, Right(${Chunk.fromIterable(right)}))",
        ) &&
        testRoutes(
          s"/users/$userId?right=${right(0)}&right=${right(1)}&left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(${Chunk.fromIterable(left)}))",
        )
      }
    },
    test("either of two multi value query parameters of the same type") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.alphaNumericString)) {
        (userId, left, right) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .query(queryAll("left") | queryAll("right"))
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
            s"path(users, $userId, ${Chunk.fromIterable(left)})",
          ) &&
          testRoutes(
            s"/users/$userId?right=${right(0)}&right=${right(1)}",
            s"path(users, $userId, ${Chunk.fromIterable(right)})",
          ) &&
          testRoutes(
            s"/users/$userId?right=${right(0)}&right=${right(1)}&left=${left(0)}&left=${left(1)}",
            s"path(users, $userId, ${Chunk.fromIterable(left)})",
          )
      }
    },
    test("either of multi value or single value query parameter") {
      check(Gen.int, Gen.listOfN(2)(Gen.alphaNumericString), Gen.alphaNumericString) { (userId, left, right) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(queryAll("left") | query("right"))
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
          s"path(users, $userId, Left(${Chunk.fromIterable(left)}))",
        ) &&
        testRoutes(
          s"/users/$userId?right=$right",
          s"path(users, $userId, Right($right))",
        ) &&
        testRoutes(
          s"/users/$userId?right=$right&left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(${Chunk.fromIterable(left)}))",
        )
      }
    },
    test("query parameters keys without values for multi value query") {
      val testRoutes = testEndpoint(
        Routes(
          Endpoint(GET / "users")
            .query(queryAllInt("ints"))
            .out[String]
            .implement {
              Handler.fromFunction { case queryParams =>
                s"path(users, $queryParams)"
              }
            },
        ),
      ) _

      testRoutes(
        s"/users?ints",
        s"path(users, ${Chunk.empty})",
      )
    },
    test("no specified query parameters for multi value query") {
      val testRoutes = Routes(
        Endpoint(GET / "users")
          .query(queryAllInt("ints"))
          .out[String]
          .implement {
            Handler.fromFunction { case queryParams =>
              s"path(users, $queryParams)"
            }
          },
      )

      testRoutes.toHttpApp
        .runZIO(Request.get("/users"))
        .map(resp => assertTrue(resp.status == Status.BadRequest))
    },
    test("multiple query parameter values to single value query parameter codec") {
      val testRoutes =
        Routes(
          Endpoint(GET / "users")
            .query(queryInt("ints"))
            .out[String]
            .implement {
              Handler.fromFunction { case queryParams =>
                s"path(users, $queryParams)"
              }
            },
        )

      testRoutes.toHttpApp
        .runZIO(Request.get(URL.decode("/users?ints=1&ints=2").toOption.get))
        .map(resp => assertTrue(resp.status == Status.BadRequest))
    },
  )
}
