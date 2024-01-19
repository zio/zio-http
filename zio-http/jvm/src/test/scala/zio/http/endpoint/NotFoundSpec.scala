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
import zio.http.endpoint._
import zio.http.forms.Fixtures.formField

object NotFoundSpec extends ZIOHttpSpec {
  def spec = suite("NotFoundSpec")(
    test("on wrong path") {
      check(Gen.int) { userId =>
        val testRoutes = test404(
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
        testRoutes(s"/user/$userId", Method.GET) &&
        testRoutes(s"/users/$userId/wrong", Method.GET)
      }
    },
    test("on wrong method") {
      check(Gen.int, Gen.int, Gen.alphaNumericString) { (userId, postId, name) =>
        val testRoutes = test404(
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
        testRoutes(s"/users/$userId", Method.POST) &&
        testRoutes(s"/users/$userId/posts/$postId?name=$name", Method.PUT)
      }
    },
  )

  def test404[R](service: Routes[R, Nothing])(
    url: String,
    method: Method,
  ): ZIO[R, Response, TestResult] = {
    val request = Request(method = method, url = URL.decode(url).toOption.get)
    for {
      response <- service.toHttpApp.runZIO(request)
      result = response.status == Status.NotFound
    } yield assertTrue(result)
  }
}
