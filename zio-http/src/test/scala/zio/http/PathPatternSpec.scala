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
  import zio.http.Method
  import zio.http.PathPattern._

  def tree     =
    suite("tree")(
      test("empty tree") {
        val tree = PathPattern.Tree.empty

        assertTrue(tree.get(Method.GET, Path("/")).isEmpty)
      },
      test("GET /users") {
        var tree: Tree[Unit] = PathPattern.Tree.empty

        val pattern = Method.GET / "users"

        tree = tree.add(pattern, ())

        assertTrue(tree.get(Method.GET, Path("/users")).nonEmpty) &&
        assertTrue(tree.get(Method.POST, Path("/users")).isEmpty)
      },
      test("GET /users/{user-id}/posts/{post-id}") {
        var tree: Tree[Unit] = PathPattern.Tree.empty

        val pattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

        tree = tree.add(pattern, ())

        assertTrue(tree.get(Method.GET, Path("/users/1/posts/abc")).nonEmpty) &&
        assertTrue(tree.get(Method.GET, Path("/users/abc/posts/1")).isEmpty)
      },
    )
  def decoding =
    suite("decoding")(
      suite("successful decoding")(
        test("GET /users") {
          assertTrue((Method.GET / "users").decode(Method.GET, Path("/users")) == Right(()))
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          val pathPattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

          assertTrue(pathPattern.decode(Method.GET, Path("/users/1/posts/abc")) == Right((1, "abc")))
        },
        test("GET /users/{user-id}/posts/{post-id}/attachments/{attachment-uuid}") {
          val pathPattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string(
            "post-id",
          ) / "attachments" / Segment.uuid("attachment-uuid")

          assertTrue(
            pathPattern.decode(
              Method.GET,
              Path("/users/1/posts/abc/attachments/123e4567-e89b-12d3-a456-426614174000"),
            ) == Right((1, "abc", java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))),
          )
        },
      ),
      suite("failed decoding")(
        test("GET /users") {
          assertTrue(
            (Method.GET / "users").decode(Method.GET, Path("/")) == Left(
              "Expected path segment \"users\" but found end of path",
            ),
          )
        },
        test("GET /users/{user-id}") {
          val pathPattern = Method.GET / "users" / Segment.int("user-id")

          assertTrue(
            pathPattern.decode(Method.GET, Path("/users/abc")) == Left(
              "Expected integer path segment but found \"abc\"",
            ),
          )
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          val pathPattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

          assertTrue(
            pathPattern.decode(Method.GET, Path("/users/1/posts/")) == Left(
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
        val pathPattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

        assertTrue(pathPattern.render == "GET /users/{user-id}/posts/{post-id}")
      },
    )

  def formatting =
    suite("formatting")(
      test("/users") {
        assertTrue((Method.GET / "users").format(()) == Path("/users"))
      },
      test("/users/{user-id}/posts/{post-id}") {
        val pathPattern = Method.GET / "users" / Segment.int("user-id") / "posts" / Segment.string("post-id")

        assertTrue(pathPattern.format((1, "abc")) == Path("/users/1/posts/abc"))
      },
    )

  def spec =
    suite("PathPatternSpec")(
      decoding,
      rendering,
      formatting,
      tree,
    )
}
