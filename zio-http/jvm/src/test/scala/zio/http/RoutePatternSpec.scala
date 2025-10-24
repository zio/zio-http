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

import java.util.UUID

import zio.test._

import zio.http.codec.Doc
import zio.http.{int => _, uuid => _}

object RoutePatternSpec extends ZIOHttpSpec {
  import zio.http.RoutePattern._

  def tree     =
    suite("tree")(
      test("wildcard route") {
        var tree: RoutePattern.Tree[Any] = RoutePattern.Tree.empty

        val routePattern = RoutePattern.any

        val handler = Handler.ok

        tree = tree.add(routePattern, handler)

        def check(method: Method, path: Path): TestResult =
          assertTrue(tree.get(method, path) == handler)

        check(Method.GET, Path("")) &&
        check(Method.GET, Path("/")) &&
        check(Method.GET, Path("/users")) &&
        check(Method.PUT, Path("/users/1")) &&
        check(Method.POST, Path("/users/1/posts")) &&
        check(Method.DELETE, Path("/users/1/posts/abc"))
      },
      test("wildcard method") {
        val routePattern = Method.ANY / "users"

        var tree: RoutePattern.Tree[Any] = RoutePattern.Tree.empty

        val handler = Handler.ok

        tree = tree.add(routePattern, handler)

        assertTrue(
          tree.getRoot.literals.size == 1,
          tree.postRoot.literals.size == 1,
          tree.putRoot.literals.size == 1,
          tree.deleteRoot.literals.size == 1,
          tree.optionsRoot.literals.size == 1,
          tree.patchRoot.literals.size == 1,
          tree.headRoot.literals.size == 1,
          tree.optionsRoot.literals.size == 1,
          tree.traceRoot.literals.size == 1,
          tree.get(Method.GET, Path("/users")) == handler,
          tree.get(Method.PUT, Path("/users")) == handler,
          tree.get(Method.POST, Path("/users")) == handler,
          tree.get(Method.DELETE, Path("/users")) == handler,
        )
      },
      test("empty tree") {
        val tree = RoutePattern.Tree.empty[Any]

        assertTrue(tree.get(Method.GET, Path("/")) == null)
      },
      test("xyz GET /users") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users"

        val handler = Handler.ok

        tree = tree.add(pattern, handler)

        assertTrue(
          tree.get(Method.GET, Path("/users")) == handler,
          tree.get(Method.POST, Path("/users")) == null,
        )
      },
      test("GET /users/{user-id}/posts/{post-id}") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        val handler = Handler.ok

        tree = tree.add(pattern, handler)

        assertTrue(
          tree.get(Method.GET, Path("/users/1/posts/abc")) == handler,
          tree.get(Method.GET, Path("/users/abc/posts/1")) == null,
        )
      },
      test("GET /users/{string-param}/{user-id:uuid} issue 3005") {
        val routePattern1 = Method.GET / "users" / string("param") / "abc" / uuid("id") / "hello"
        val routePattern2 = Method.GET / "users" / string("param") / uuid("id") / "hello"

        val id              = UUID.randomUUID()
        var tree: Tree[Any] = RoutePattern.Tree.empty
        val handler1        = Handler.ok
        val handler2        = Handler.notFound
        tree = tree.add(routePattern1, handler1)
        tree = tree.add(routePattern2, handler2)

        val p1 = Path(s"/users/some_value/abc/$id/hello")
        val p2 = Path(s"/users/some_value/$id/hello")
        assertTrue(
          routePattern1.decode(Method.GET, p1) == Right(("some_value", id)),
          routePattern2.decode(Method.GET, p2) == Right(("some_value", id)),
          tree.get(Method.GET, p1) == handler1,
          tree.get(Method.GET, p2) == handler2,
        )
      },
      suite("collisions properly resolved")(
        test("simple collision between literal and text segment i3036") {
          val tree: Tree[Any] = Tree
            .empty[Any]
            .addAll(
              List(
                (Method.GET / "users" / "param1" / "fixed", Handler.ok),
                (Method.GET / "users" / string("param") / "dynamic", Handler.notFound),
              ),
            )

          assertTrue(
            tree.get(Method.GET, Path("/users/param1/fixed")) == Handler.ok,
            tree.get(Method.GET, Path("/users/param1/dynamic")) == Handler.notFound,
          )
        },
        test("two collisions between literal and text segment") {
          val tree: Tree[Any] = Tree
            .empty[Any]
            .addAll(
              List(
                (Method.GET / "users" / "param1" / "literal1" / "p1" / "tail1", Handler.ok),
                (Method.GET / "users" / "param1" / "literal1" / string("p2") / "tail2", Handler.notFound),
                (Method.GET / "users" / string("param") / "literal1" / "p1" / "tail3", Handler.badRequest),
                (Method.GET / "users" / string("param") / "literal1" / string("p2") / "tail4", Handler.tooLarge),
              ),
            )

          assertTrue(
            tree.get(Method.GET, Path("/users/param1/literal1/p1/tail1")) == Handler.ok,
            tree.get(Method.GET, Path("/users/param1/literal1/p1/tail2")) == Handler.notFound,
            tree.get(Method.GET, Path("/users/param1/literal1/p1/tail3")) == Handler.badRequest,
            tree.get(Method.GET, Path("/users/param1/literal1/p1/tail4")) == Handler.tooLarge,
          )
        },
        test("collision where distinguish is by literal and int segment") {
          val tree: Tree[Any] = Tree
            .empty[Any]
            .addAll(
              List(
                (Method.GET / "users" / "param1" / int("id"), Handler.ok),
                (Method.GET / "users" / string("param") / "dynamic", Handler.notFound),
              ),
            )

          val r1 = tree.get(Method.GET, Path("/users/param1/155"))
          val r2 = tree.get(Method.GET, Path("/users/param1/dynamic"))

          assertTrue(
            r1 == Handler.ok,
            r2 == Handler.notFound,
          )
        },
        test("collision where distinguish is by two not literal segments") {
          val uuid1           = new UUID(10, 10)
          val tree: Tree[Any] = Tree
            .empty[Any]
            .addAll(
              List(
                (Method.GET / "users" / "param1" / int("id"), Handler.ok),
                (Method.GET / "users" / string("param") / uuid("dynamic"), Handler.notFound),
              ),
            )
          val r2              = tree.get(Method.GET, Path(s"/users/param1/$uuid1"))
          val r1              = tree.get(Method.GET, Path("/users/param1/155"))

          assertTrue(
            r1 == Handler.ok,
            r2 == Handler.notFound,
          )
        },
      ),
      test("on conflict, last one wins") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users"
        val pattern2 = Method.GET / "users"

        tree = tree.add(pattern1, Handler.ok)
        tree = tree.add(pattern2, Handler.notFound)

        assertTrue(tree.get(Method.GET, Path("/users")) == Handler.notFound)
      },
      test("on conflict, trailing loses") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users" / "123"
        val pattern2 = Method.GET / "users" / trailing / "123"

        tree = tree.add(pattern2, Handler.ok)
        tree = tree.add(pattern1, Handler.notFound)

        tree.get(Method.GET, Path("/users/123"))(Request()).map(r => assertTrue(r.status == Status.NotFound))
      },
      test("more specific beats less specific") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern1 = Method.ANY / "users"
        val pattern2 = Method.OPTIONS / "users"

        tree = tree.add(pattern1, Handler.ok)
        tree = tree.add(pattern2, Handler.notFound)

        assertTrue(tree.get(Method.OPTIONS, Path("/users")) == Handler.notFound)
      },
      test("multiple routes") {
        var tree: Tree[Unit] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users"
        val pattern2 = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        tree = tree.add(pattern1, Handler.ok)
        tree = tree.add(pattern2, Handler.notFound)

        assertTrue(
          tree.get(Method.GET, Path("/users")) == Handler.ok,
          tree.get(Method.GET, Path("/users/1/posts/abc")) == Handler.notFound,
        )
      },
      test("overlapping routes") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern1 = Method.GET / "users" / int("user-id")
        val pattern2 = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        tree = tree.add(pattern1, Handler.ok)
        tree = tree.add(pattern2, Handler.notFound)

        assertTrue(
          tree.get(Method.GET, Path("/users/1")) == Handler.ok,
          tree.get(Method.GET, Path("/users/1/posts/abc")) == Handler.notFound,
        )
      },
      test("get with prefix") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users"

        tree = tree.add(pattern, Handler.ok)

        assertTrue(
          tree.get(Method.GET, Path("/users/1")) == null,
        )
      },
      test("trailing route pattern can handle all paths") {
        var tree: Tree[Any] = RoutePattern.Tree.empty

        val pattern = Method.GET / "users" / trailing

        tree = tree.add(pattern, Handler.ok)

        assertTrue(
          tree.get(Method.GET, Path("/posts/")) == null,
          tree.get(Method.GET, Path("/users/1")) == Handler.ok,
          tree.get(Method.GET, Path("/users/1/posts/abc")) == Handler.ok,
          tree.get(Method.GET, Path("/users/1/posts/abc/def")) == Handler.ok,
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
              {
                routePattern.decode(
                  Method.GET,
                  Path("/users"),
                ) == Right(())
              }, {
                routePattern.decode(
                  Method.PUT,
                  Path("/users"),
                ) == Right(())
              }, {
                routePattern.decode(
                  Method.POST,
                  Path("/users"),
                ) == Right(())
              }, {
                routePattern.decode(
                  Method.DELETE,
                  Path("/users"),
                ) == Right(())
              },
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
        assertTrue((Method.GET / "users").format(()) == Right(Path("/users")))
      },
      test("/users/{user-id}/posts/{post-id}") {
        val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

        assertTrue(routePattern.format((1, "abc")) == Right(Path("/users/1/posts/abc")))
      },
      test("formatting root") {
        val routePattern = Method.GET / Root
        assertTrue(routePattern.format(()) == Right(Path("/")))
      },
    )

  def structureEquals = suite("structure equals")(
    test("equals") {
      val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

      assertTrue(routePattern.structureEquals(routePattern))
    },
    test("equals with docs") {
      val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")

      assertTrue(
        routePattern.structureEquals(routePattern ?? Doc.p("docs")),
      )
    },
    test("equals with mapping") {
      val routePattern  = Method.GET / "users" / int("user-id") / "posts" / string("post-id")
      val routePattern1 =
        Method.GET / "users" / int("user-id").transform(_.toString())(_.toInt) / "posts" / string("post-id")

      assertTrue(
        routePattern.structureEquals(routePattern1),
      )
    },
  )

  def spec =
    suite("RoutePatternSpec")(
      decoding,
      rendering,
      formatting,
      tree,
      structureEquals,
    )
}
