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

package zio.http

import scala.collection.Seq

import zio.Chunk
import zio.test._

import zio.http.internal.HttpGen

object PathPatternSpec extends ZIOSpecDefault {
  def decoding =
    suite("decoding")(
      suite("successful decoding")(
        test("GET /users") {
          import zio.http.Method

          assertTrue((Method.GET / "users").decode(Method.GET, Path("/users")) == Right(()))
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          import zio.http.Method
          import zio.http.PathPattern._

          val pathSpec = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

          assertTrue(pathSpec.decode(Method.GET, Path("/users/1/posts/abc")) == Right((1, "abc")))
        },
        test("GET /users/{user-id}/posts/{post-id}/attachments/{attachment-uuid}") {
          import zio.http.Method
          import zio.http.PathPattern._

          val pathSpec = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string(
            "post-id",
          ) / "attachments" / Segment.uuid("attachment-uuid")

          assertTrue(
            pathSpec.decode(
              Method.GET,
              Path("/users/1/posts/abc/attachments/123e4567-e89b-12d3-a456-426614174000"),
            ) == Right((1, "abc", java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))),
          )
        },
      ),
      suite("failed decoding")(
        test("GET /users") {
          import zio.http.Method

          assertTrue(
            (Method.GET / "users").decode(Method.GET, Path("/")) == Left(
              "Expected path segment \"users\" but found end of path",
            ),
          )
        },
        test("GET /users/{user-id}") {
          import zio.http.Method
          import zio.http.PathPattern._

          val pathSpec = Method.GET / "users" / Segment.int("user-id")

          assertTrue(
            pathSpec.decode(Method.GET, Path("/users/abc")) == Left("Expected integer path segment but found \"abc\""),
          )
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          import zio.http.Method
          import zio.http.PathPattern._

          val pathSpec = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

          assertTrue(
            pathSpec.decode(Method.GET, Path("/users/1/posts/")) == Left(
              "Expected text path segment but found end of path",
            ),
          )
        },
      ),
    )

  def rendering =
    suite("rendering")(
      test("GET /users") {
        import zio.http.Method

        assertTrue((Method.GET / "users").render == "GET /users")
      },
      test("GET /users/{user-id}/posts/{post-id}") {
        import zio.http.Method
        import zio.http.PathPattern._

        val pathSpec = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

        assertTrue(pathSpec.render == "GET /users/{user-id}/posts/{post-id}")
      },
    )

  def formatting =
    suite("formatting")(
      test("/users") {
        import zio.http.Method

        assertTrue((Method.GET / "users").format(()) == Path("/users"))
      },
      test("/users/{user-id}/posts/{post-id}") {
        import zio.http.Method
        import zio.http.PathPattern._

        val pathSpec = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

        assertTrue(pathSpec.format((1, "abc")) == Path("/users/1/posts/abc"))
      },
    )

  def spec =
    suite("PathPatternSpec")(
      decoding,
      rendering,
      formatting,
    )
}
