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

import zio.schema.{DeriveSchema, Schema}

import zio.http.Method._
import zio.http._
import zio.http.codec._
import zio.http.endpoint.EndpointSpec.testEndpoint

object QueryParameterSpec extends ZIOHttpSpec {
  case class Params(
    int: Int,
    optInt: Option[Int] = None,
    string: String,
    strings: Chunk[String] = Chunk("defaultString"),
  )
  implicit val paramsSchema: Schema[Params] = DeriveSchema.gen[Params]

  def spec = suite("QueryParameterSpec")(
    test("Query parameters from case class") {
      check(
        Gen.int,
        Gen.option(Gen.int),
        Gen.alphaNumericStringBounded(0, 10),
        Gen.chunkOf(Gen.alphaNumericStringBounded(0, 10)),
      ) { (int, optInt, string, strings) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users")
              .query(HttpCodec.query[Params])
              .out[String]
              .implementPurely(_.toString),
          ),
        ) _

        testRoutes(
          s"/users?int=$int&optInt=${optInt.mkString}&string=$string&strings=${strings.mkString(",")}",
          Params(int, optInt, string, strings).toString,
        )
      }
    },
    test("simple request with query parameter") {
      check(Gen.int, Gen.int, Gen.alphaNumericString) { (userId, postId, username) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .out[String]
              .implementHandler {
                Handler.fromFunction { userId =>
                  s"path(users, $userId)"
                }
              },
            Endpoint(GET / "users" / int("userId") / "posts" / int("postId"))
              .query(HttpCodec.query[String]("name"))
              .out[String]
              .implementHandler {
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
    test("single optional query parameter") {
      check(Gen.int, Gen.alphaNumericString) { (userId, details) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(HttpCodec.query[String]("details").optional)
              .out[String]
              .implementHandler {
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
              .query(HttpCodec.query[String]("key").optional)
              .query(HttpCodec.query[String]("value").optional)
              .out[String]
              .implementHandler {
                Handler.fromFunction { case (userId, key, value) =>
                  s"path(users, $userId, $key, $value)"
                }
              },
          ),
        ) _
        testRoutes(s"/users/$userId", s"path(users, $userId, None, None)") &&
        testRoutes(s"/users/$userId?key=&value=", s"path(users, $userId, Some(), Some())") &&
        testRoutes(s"/users/$userId?key=&value=$value", s"path(users, $userId, Some(), ${Some(value)})") &&
        testRoutes(
          s"/users/$userId?key=$key&value=$value",
          s"path(users, $userId, ${Some(key)}, ${Some(value)})",
        )
      }
    },
    test("query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString)) { (userId, keys) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(HttpCodec.query[Chunk[String]]("key"))
              .out[String]
              .implementHandler {
                Handler.fromFunction { case (userId, keys) =>
                  s"""path(users, $userId, ${keys.mkString(", ")})"""
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}",
          s"path(users, $userId, ${keys.mkString(", ")})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}",
          s"path(users, $userId, ${keys.take(2).mkString(", ")})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}",
          s"path(users, $userId, ${keys.take(1).mkString(", ")})",
        )
      }
    },
    test("query parameters with multiple values non empty") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString)) { (userId, keys) =>
        val routes     = Routes(
          Endpoint(GET / "users" / int("userId"))
            .query(HttpCodec.query[NonEmptyChunk[String]]("key"))
            .out[String]
            .implementHandler {
              Handler.fromFunction { case (userId, keys) =>
                s"""path(users, $userId, ${keys.mkString(", ")})"""
              }
            },
        )
        val testRoutes = testEndpoint(
          routes,
        ) _

        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}",
          s"path(users, $userId, ${keys.mkString(", ")})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}",
          s"path(users, $userId, ${keys.take(2).mkString(", ")})",
        ) &&
        testRoutes(
          s"/users/$userId?key=${keys(0)}",
          s"path(users, $userId, ${keys.take(1).mkString(", ")})",
        ) && routes
          .runZIO(Request.get(s"/users/$userId"))
          .map(resp => assertTrue(resp.status == Status.BadRequest))
      }
    },
    test("optional query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString)) { (userId, keys) =>
        val testRoutes = testEndpoint(
          Routes(
            Endpoint(GET / "users" / int("userId"))
              .query(HttpCodec.query[Chunk[String]]("key").optional)
              .out[String]
              .implementHandler {
                Handler.fromFunction { case (userId, keys) =>
                  s"""path(users, $userId, $keys)"""
                }
              },
          ),
        ) _

        testRoutes(
          s"/users/$userId?key=${keys(0)}&key=${keys(1)}&key=${keys(2)}",
          s"path(users, $userId, ${s"Some(${Chunk.fromIterable(keys)})"})",
        ) &&
        testRoutes(
          s"/users/$userId",
          s"path(users, $userId, None)",
        ) &&
        testRoutes(
          s"/users/$userId?key=",
          s"path(users, $userId, Some(${Chunk("")}))",
        )
      }
    },
    test("multiple query parameters with multiple values") {
      check(Gen.int, Gen.listOfN(3)(Gen.alphaNumericString), Gen.listOfN(2)(Gen.alphaNumericString)) {
        (userId, keys, values) =>
          val testRoutes = testEndpoint(
            Routes(
              Endpoint(GET / "users" / int("userId"))
                .query(HttpCodec.query[Chunk[String]]("key") & HttpCodec.query[Chunk[String]]("value"))
                .out[String]
                .implementHandler {
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
              .query(HttpCodec.query[Chunk[String]]("multi") & HttpCodec.query[String]("single"))
              .out[String]
              .implementHandler {
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
              .query(HttpCodec.query[Chunk[String]]("left") | HttpCodec.query[Chunk[Boolean]]("right"))
              .out[String]
              .implementHandler {
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
        // TODO: Fix this test when we have non empty collections support
        // currently it fails because of the empty collection for left
//        testRoutes(
//          s"/users/$userId?right=${right(0)}&right=${right(1)}",
//          s"path(users, $userId, Right(${Chunk.fromIterable(right)}))",
//        ) &&
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
                .query(HttpCodec.query[Chunk[String]]("left") | HttpCodec.query[Chunk[String]]("right"))
                .out[String]
                .implementHandler {
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
          // TODO: Fix this test when we have non empty collections support
          // currently it fails because of the empty collection for left
//          testRoutes(
//            s"/users/$userId?right=${right(0)}&right=${right(1)}",
//            s"path(users, $userId, ${Chunk.fromIterable(right)})",
//          ) &&
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
              .query(HttpCodec.query[Chunk[String]]("left") | HttpCodec.query[String]("right"))
              .out[String]
              .implementHandler {
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
        // TODO: Fix this test when we have non empty collections support
        // currently it fails because of the empty collection for left
//        testRoutes(
//          s"/users/$userId?right=$right",
//          s"path(users, $userId, Right($right))",
//        ) &&
        testRoutes(
          s"/users/$userId?right=$right&left=${left(0)}&left=${left(1)}",
          s"path(users, $userId, Left(${Chunk.fromIterable(left)}))",
        )
      }
    },
    test("query parameters keys without values for multi value query") {
      val routes     = Routes(
        Endpoint(GET / "users")
          .query(HttpCodec.query[Chunk[Int]]("ints"))
          .out[String]
          .implementHandler {
            Handler.fromFunction { queryParams => s"path(users, $queryParams)" }
          },
      )
      val testRoutes = testEndpoint(
        routes,
      ) _

      routes
        .runZIO(Request.get("/users").addQueryParam("ints", ""))
        .map(resp => assertTrue(resp.status == Status.BadRequest)) &&
      testRoutes(
        s"/users",
        s"path(users, ${Chunk.empty})",
      )
    },
    test("no specified query parameters for multi value query") {
      val testRoutes = Routes(
        Endpoint(GET / "users")
          .query(HttpCodec.query[Int]("ints"))
          .out[String]
          .implementHandler {
            Handler.fromFunction { queryParams =>
              s"path(users, $queryParams)"
            }
          },
      )

      testRoutes
        .runZIO(Request.get("/users"))
        .map(resp => assertTrue(resp.status == Status.BadRequest))
    },
    test("multiple query parameter values to single value query parameter codec") {
      val testRoutes =
        Routes(
          Endpoint(GET / "users")
            .query(HttpCodec.query[Int]("ints"))
            .out[String]
            .implementHandler {
              Handler.fromFunction { queryParams =>
                s"path(users, $queryParams)"
              }
            },
        )

      testRoutes
        .runZIO(Request.get(url"/users?ints=1&ints=2"))
        .map(resp => assertTrue(resp.status == Status.BadRequest))
    },
    test("Many optional query params don't blow up the stack") {
      type SOIn = (
        (
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
        ),
        Option[String],
        Option[String],
        Option[String],
        Option[String],
        Option[String],
      )
      val soEndpoint =
        Endpoint(Method.GET / "so")
          .query[Option[String]](HttpCodec.query[String]("a").optional)
          .query[Option[String]](HttpCodec.query[String]("b").optional)
          .query[Option[String]](HttpCodec.query[String]("c").optional)
          .query[Option[String]](HttpCodec.query[String]("d").optional)
          .query[Option[String]](HttpCodec.query[String]("e").optional)
          .query[Option[String]](HttpCodec.query[String]("f").optional)
          .query[Option[String]](HttpCodec.query[String]("g").optional)
          .query[Option[String]](HttpCodec.query[String]("h").optional)
          .query[Option[String]](HttpCodec.query[String]("i").optional)
          .query[Option[String]](HttpCodec.query[String]("j").optional)
          .query[Option[String]](HttpCodec.query[String]("k").optional)
          .query[Option[String]](HttpCodec.query[String]("l").optional)
          .query[Option[String]](HttpCodec.query[String]("m").optional)
          .query[Option[String]](HttpCodec.query[String]("n").optional)
          .query[Option[String]](HttpCodec.query[String]("o").optional)
          .out[String]

      val soHandler: Handler[Any, zio.ZNothing, SOIn, String] = Handler.fromZIO(ZIO.succeed(""))
      val soRoute: Route[Any, Nothing]                        = soEndpoint.implementHandler(soHandler)

      soRoute.run(Request.get("/so")).map { response =>
        assertTrue(response.status == Status.Ok)
      }
    },
  ).provide(ErrorResponseConfig.debugLayer)

}
