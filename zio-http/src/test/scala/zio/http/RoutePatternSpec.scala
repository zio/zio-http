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

import zio.http.codec.PathCodec._
import zio.http.internal.HttpGen
import zio.http.{int => _, uuid => _, _}

object RoutePatternSpec extends ZIOSpecDefault {
  import zio.http.Method
  import zio.http.RoutePattern._

  def tree     =
    suite("tree")(
      test("wildcard route") {
        var tree: RoutePattern.Tree[Int] = RoutePattern.Tree.empty

        val routePattern = RoutePattern.any

        tree = tree.add(routePattern, 42)

        def check(method: Method, path: Path): TestResult =
          assertTrue(tree.get(method, path) == Chunk(42))

        check(Method.GET, Path("/"))
        // check(Method.GET, Path("/users")) &&
        // check(Method.PUT, Path("/users/1")) &&
        // check(Method.POST, Path("/users/1/posts")) &&
        // check(Method.DELETE, Path("/users/1/posts/abc"))
      },
      test("wildcard method") {
        val routePattern = Method.ANY / "users"

        var tree: RoutePattern.Tree[Unit] = RoutePattern.Tree.empty

        tree = tree.add(routePattern, ())

        assertTrue(
          tree
            .get(
              Method.GET,
              Path("/users"),
            )
            .nonEmpty,
        ) &&
        assertTrue(
          tree
            .get(
              Method.PUT,
              Path("/users"),
            )
            .nonEmpty,
        ) &&
        assertTrue(
          tree
            .get(
              Method.POST,
              Path("/users"),
            )
            .nonEmpty,
        ) && assertTrue(
          tree
            .get(
              Method.DELETE,
              Path("/users"),
            )
            .nonEmpty,
        )
      },
      test("empty tree") {
        val tree = RoutePattern.Tree.empty

        assertTrue(tree.get(Method.GET, Path("/")).isEmpty)
      },
      test("xyz GET /users") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users"

        tree = tree.add(pattern, ())

        assertTrue(tree.get(Method.GET, Path("/users")).nonEmpty) &&
        assertTrue(tree.get(Method.POST, Path("/users")).isEmpty)
      },
      test("GET /users/{user-id}/posts/{post-id}") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        tree = tree.add(pattern, ())

        assertTrue(tree.get(Method.GET, Path("/users/1/posts/abc")).nonEmpty) &&
        assertTrue(tree.get(Method.GET, Path("/users/abc/posts/1")).isEmpty)
      },
      test("on conflict, first one wins") {
        var tree: Tree[Int] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users"
        val pattern2 = Method.GET / "users"

        tree = tree.add(pattern1, 1)
        tree = tree.add(pattern2, 2)

        assertTrue(tree.get(Method.GET, Path("/users")).contains(1))
      },
      test("on conflict, trailing loses") {
        var tree: Tree[Int] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users" / "123"
        val pattern2 = Method.GET / "users" / trailing

        tree = tree.add(pattern2, 2)
        tree = tree.add(pattern1, 1)

        assertTrue(tree.get(Method.GET, Path("/users/123")).contains(1))
      },
      test("multiple routes") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users"
        val pattern2 = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        tree = tree.add(pattern1, ())
        tree = tree.add(pattern2, ())

        assertTrue(tree.get(Method.GET, Path("/users")).nonEmpty) &&
        assertTrue(tree.get(Method.GET, Path("/users/1/posts/abc")).nonEmpty)
      },
      test("overlapping routes") {
        var tree: Tree[Int] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users" / int("user-id")
        val pattern2 = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        tree = tree.add(pattern1, 1)
        tree = tree.add(pattern2, 2)

        assertTrue(tree.get(Method.GET, Path("/users/1")).contains(1)) &&
        assertTrue(tree.get(Method.GET, Path("/users/1/posts/abc")).contains(2))
      },
      test("get with prefix") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users"

        tree = tree.add(pattern, ())

        assertTrue(
          tree.get(Method.GET, Path("/users/1")).isEmpty,
        )
      },
      test("trailing route pattern can handle all paths") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users" / trailing

        tree = tree.add(pattern, ())

        assertTrue(
          tree.get(Method.GET, Path("/posts/")).isEmpty,
        ) &&
        assertTrue(
          tree.get(Method.GET, Path("/users/1")).nonEmpty,
        ) &&
        assertTrue(
          tree.get(Method.GET, Path("/users/1/posts/abc")).nonEmpty,
        ) &&
        assertTrue(
          tree.get(Method.GET, Path("/users/1/posts/abc/def")).nonEmpty,
        )
      },
    )
  def decoding =
    suite("decoding")(
      suite("auto-parsing of paths")(
        test("root equivalence") {
          val variant1 = RoutePattern(Method.GET, zio.http.codec.PathCodec.empty)
          val variant2 = Method.GET / ""

          assertTrue(
            variant1.decode(Method.GET, Path("/")) == variant2.decode(Method.GET, Path("/")),
          ) &&
          assertTrue(
            variant1.decode(Method.GET, Path("/users")) == variant2.decode(Method.GET, Path("/users")),
          )
        },
      ),
      suite("successful decoding")(
        test("GET /") {
          assertTrue((Method.GET / "").decode(Method.GET, Path("/")) == Right(()))
        },
        test("GET /users") {
          assertTrue((Method.GET / "users").decode(Method.GET, Path("/users")) == Right(()))
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          val routePattern =
            Method.GET / "users" / int("user-id") / "posts" / string("post-id")

          assertTrue(routePattern.decode(Method.GET, Path("/users/1/posts/abc")) == Right((1, "abc")))
        },
        test("GET /users/{user-id}/posts/{post-id}/attachments/{attachment-uuid}") {
          val routePattern = Method.GET / "users" / int("user-id") / "posts" / string(
            "post-id",
          ) / "attachments" / uuid("attachment-uuid")

          assertTrue(
            routePattern.decode(
              Method.GET,
              Path("/users/1/posts/abc/attachments/123e4567-e89b-12d3-a456-426614174000"),
            ) == Right((1, "abc", java.util.UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))),
          )
        },
        suite("wildcard")(
          test("GET/POST/PUT/DELETE /users") {
            val routePattern = Method.ANY / "users"

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users"),
              ) == Right(()),
            ) &&
            assertTrue(
              routePattern.decode(
                Method.PUT,
                Path("/users"),
              ) == Right(()),
            ) &&
            assertTrue(
              routePattern.decode(
                Method.POST,
                Path("/users"),
              ) == Right(()),
            ) && assertTrue(
              routePattern.decode(
                Method.DELETE,
                Path("/users"),
              ) == Right(()),
            )
          },
          test("* ...") {
            def test(method: Method, path: Path): TestResult =
              assertTrue(RoutePattern.any.decode(method, path).isRight)

            test(Method.GET, Path("/")) &&
            test(Method.GET, Path("/")) &&
            test(Method.GET, Path("/users")) &&
            test(Method.PUT, Path("/users/1")) &&
            test(Method.POST, Path("/users/1/posts")) &&
            test(Method.DELETE, Path("/users/1/posts/abc"))
          },
        ),
        suite("trailing")(
          test("GET /users/1 on prefix") {
            val routePattern = Method.GET / "users"

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users/1"),
              ) == Left("Expected end of path but found: 1"),
            )
          },
          test("GET /users/1 on prefix with trailing") {
            val routePattern = Method.GET / "users" / trailing

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users/1"),
              ) == Right(Path("1")),
            )
          },
          test("GET /users/1/posts/abc with long trailing") {
            val routePattern =
              Method.GET / "users" / int("user-id") / trailing

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users/1/posts/abc/def/ghi"),
              ) == Right((1, Path("posts/abc/def/ghi"))),
            )
          },
          test("trailing slash matches root") {
            val routePattern = Method.GET / "users" / trailing

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users/"),
              ) == Right(Path.root),
            )
          },
          test("trailing without slash matches empty") {
            val routePattern = Method.GET / "users" / trailing

            assertTrue(
              routePattern.decode(
                Method.GET,
                Path("/users"),
              ) == Right(Path.empty),
            )
          },
        ),
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
          val routePattern = Method.GET / "users" / int("user-id")

          assertTrue(
            routePattern.decode(Method.GET, Path("/users/abc")) == Left(
              "Expected integer path segment but found \"abc\"",
            ),
          )
        },
        test("GET /users/{user-id}/posts/{post-id}") {
          val routePattern =
            Method.GET / "users" / int("user-id") / "posts" / string("post-id")

          assertTrue(
            routePattern.decode(Method.GET, Path("/users/1/posts/")) == Left(
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
        val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        assertTrue(routePattern.render == "GET /users/{user-id}/posts/{post-id}")
      },
    )

  def formatting =
    suite("formatting")(
      test("/users") {
        assertTrue((Method.GET / "users").format(()) == Path("/users"))
      },
      test("/users/{user-id}/posts/{post-id}") {
        val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        assertTrue(routePattern.format((1, "abc")) == Path("/users/1/posts/abc"))
      },
    )

  def spec =
    suite("RoutePatternSpec")(
      decoding,
      rendering,
      formatting,
      tree,
    )
}
