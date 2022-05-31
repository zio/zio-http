package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object PathSpec extends ZIOSpecDefault with HExitAssertion {
  def collect[A](pf: PartialFunction[Path, A]): Path => Option[A] = path => pf.lift(path)
  def spec                                                        =
    suite("Path")(
      suite("Syntax")(
        suite("/")(
          testM("isDefined") {

            val gen = Gen.elements(
              // Exact
              collect { case !! => true }                   -> !!,
              collect { case !! / "a" => true }             -> !! / "a",
              collect { case !! / "a" => true }             -> !! / "a",
              collect { case !! / "a" / "b" => true }       -> !! / "a" / "b",
              collect { case !! / "a" / "b" / "c" => true } -> !! / "a" / "b" / "c",

              // Wildcards
              collect { case !! / _ => true }         -> !! / "a",
              collect { case !! / _ / _ => true }     -> !! / "a" / "b",
              collect { case !! / _ / _ / _ => true } -> !! / "a" / "b" / "c",

              // Wildcard mix
              collect { case _ / "c" => true }     -> !! / "a" / "b" / "c",
              collect { case _ / _ / "c" => true } -> !! / "a" / "b" / "c",
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isDefined)
            }
          },
        ) +
        suite("asString")(
          test("a, b, c") {
            val path = Path("a", "b", "c").encode
            assert(path)(equalTo("/a/b/c"))
          } +
            test("Path()") {
              val path = Path().encode
              assert(path)(equalTo("/"))
            } +
            test("!!") {
              val path = !!.encode
              assert(path)(equalTo("/"))
            },
        ) +
        suite("PathSyntax /")(
          test("construction") {
            val path = !! / "a" / "b" / "c"
            assert(path)(equalTo(Path("a", "b", "c")))
          } +
            test("extract path / a / b / c") {
              val path = collect { case !! / "a" / b / c => (b, c) }
              assert(path(Path("a", "b", "c")))(isSome(equalTo(("b", "c"))))
            } +
            test("extract path / a / b / c") {
              val path = collect { case !! / "a" / b => b }
              assert(path(Path("a", "b", "c")))(isNone)
            } +
            test("extract path / a / b / c") {
              val path = collect { case !! / "a" / b => b }
              assert(path(Path("a", "b")))(isSome(equalTo("b")))
            },
        ) +
        suite("PathSyntax /:")(
          test("construction") {
            val path = "a" /: "b" /: "c" /: !!
            assert(path)(equalTo(Path("a", "b", "c")))
          } +
            suite("default")(
              test("extract path 'name' /: name") {
                val path = collect { case "name" /: name => name.encode }
                assert(path(Path("name", "a", "b", "c")))(isSome(equalTo("/a/b/c")))
              } +
                test("extract paths 'name' /: a /: b /: 'c' /: !!") {
                  val path = collect { case "name" /: a /: b /: "c" /: !! => (a, b) }
                  assert(path(Path("name", "a", "b", "c")))(isSome(equalTo(("a", "b"))))
                } +
                test("extract paths 'name' /: a /: b /: _") {
                  val path = collect { case "name" /: a /: b /: _ => (a, b) }
                  assert(path(Path("name", "a", "b", "c")))(isSome(equalTo(("a", "b"))))
                } +
                test("extract paths 'name' /: name /: 'a' /: 'b' /: 'c' /: !!") {
                  val path = collect { case "name" /: name /: "a" /: "b" /: "c" /: !! => name.toString }
                  assert(path(Path("name", "Xyz", "a", "b", "c")))(isSome(equalTo("Xyz")))
                },
            ) +
            suite("int()")(
              test("extract path 'user' /: int(1)") {
                val path = collect { case "user" /: int(age) /: !! => age }
                assert(path(Path("user", "1")))(isSome(equalTo(1)))
              } +
                test("extract path 'user' /: int(Xyz)") {
                  val path = collect { case "user" /: int(age) /: !! => age }
                  assert(path(Path("user", "Xyz")))(isNone)
                },
            ) +
            suite("boolean()")(
              test("extract path 'user' /: boolean(true)") {
                val path = collect { case "user" /: boolean(ok) /: !! => ok }
                assert(path(Path("user", "True")))(isSome(isTrue))
              } +
                test("extract path 'user' /: boolean(false)") {
                  val path = collect { case "user" /: boolean(ok) /: !! => ok }
                  assert(path(Path("user", "false")))(isSome(isFalse))
                },
            ),
        ) +
        suite("startsWith")(
          test("isTrue") {
            assert(!! / "a" / "b" / "c" / "d" startsWith !! / "a")(isTrue) &&
            assert(!! / "a" / "b" / "c" / "d" startsWith !! / "a" / "b")(isTrue) &&
            assert(!! / "a" / "b" / "c" / "d" startsWith !! / "a" / "b" / "c")(isTrue) &&
            assert(!! / "a" / "b" / "c" / "d" startsWith !! / "a" / "b" / "c" / "d")(isTrue)
          } +
            test("isFalse") {
              assert(!! / "a" / "b" / "c" / "d" startsWith !! / "a" / "b" / "c" / "d" / "e")(isFalse) &&
              assert(!! / "a" / "b" / "c" startsWith !! / "a" / "b" / "c" / "d")(isFalse) &&
              assert(!! / "a" / "b" startsWith !! / "a" / "b" / "c")(isFalse) &&
              assert(!! / "a" startsWith !! / "a" / "b")(isFalse)
            } +
            test("isFalse") {
              assert(!! / "abcd" startsWith !! / "a")(isFalse)
            },
        ) +
        test("drop") {
          assert(!! / "a" / "b" / "c" drop 1)(equalTo(!! / "b" / "c")) &&
          assert(!! drop 1)(equalTo(!!))
        } +
        test("dropLast") {
          assert(!! / "a" / "b" / "c" dropLast 1)(equalTo(!! / "a" / "b")) &&
          assert(!! dropLast 1)(equalTo(!!))
        } +
        test("take") {
          assert(!! / "a" / "b" / "c" take 1)(equalTo(!! / "a")) &&
          assert(!! take 1)(equalTo(!!))
        },
      ),
      testM("take") {
        val gen = Gen.elements(
          (1, !!)                   -> !!,
          (1, !! / "a")             -> !! / "a",
          (1, !! / "a" / "b")       -> !! / "a",
          (1, !! / "a" / "b" / "c") -> !! / "a",
          (2, !! / "a" / "b" / "c") -> !! / "a" / "b",
          (3, !! / "a" / "b" / "c") -> !! / "a" / "b" / "c",
          (4, !! / "a" / "b" / "c") -> !! / "a" / "b" / "c",
        )

        checkAll(gen) { case ((n, path), expected) =>
          val actual = path.take(n)
          assertTrue(actual == expected)
        }
      },
      testM("drop") {
        val gen = Gen.elements(
          (1, !!)                   -> !!,
          (1, !! / "a")             -> !!,
          (1, !! / "a" / "b")       -> !! / "b",
          (1, !! / "a" / "b" / "c") -> !! / "b" / "c",
          (2, !! / "a" / "b" / "c") -> !! / "c",
          (3, !! / "a" / "b" / "c") -> !!,
          (4, !! / "a" / "b" / "c") -> !!,
        )

        checkAll(gen) { case ((n, path), expected) =>
          val actual = path.drop(n)
          assertTrue(actual == expected)
        }
      },
      testM("dropLast") {
        val gen = Gen.elements(
          (1, !!)                   -> !!,
          (1, !! / "a")             -> !!,
          (1, !! / "a" / "b")       -> !! / "a",
          (1, !! / "a" / "b" / "c") -> !! / "a" / "b",
          (2, !! / "a" / "b" / "c") -> !! / "a",
          (3, !! / "a" / "b" / "c") -> !!,
          (4, !! / "a" / "b" / "c") -> !!,
        )

        checkAll(gen) { case ((n, path), expected) =>
          val actual = path.dropLast(n)
          assertTrue(actual == expected)
        }
      },
      suite("encode/decode/encode")(
        testM("anyPath") {
          check(HttpGen.path) { path =>
            val expected = path.encode
            val decoded  = Path.decode(expected)

            assertTrue(decoded.encode == expected) &&
            assertTrue(decoded.toString() == expected)
          }
        },
        testM("is symmetric") {
          check(HttpGen.path) { path =>
            val expected = path.encode
            val actual   = Path.decode(expected)
            assertTrue(actual == path)
          }
        },
        testM("string elements") {
          val gen = Gen.elements(
            // basic
            "",
            "/",

            // with leading slash
            "/a",
            "/a/b",
            "/a/b/c",

            // without leading slash
            "a",
            "a/b",
            "a/b/c",

            // with trailing slash
            "a/",
            "a/b/",
            "a/b/c/",

            // with leading & trailing slash
            "/a/",
            "/a/b/",
            "/a/b/c/",

            // with encoded chars
            "a/b%2Fc",
            "/a/b%2Fc",
            "a/b%2Fc/",
            "/a/b%2Fc/",
          )

          checkAll(gen) { path =>
            val actual   = path
            val expected = Path.decode(actual).encode
            assertTrue(actual == expected)
          }
        },
        testM("path elements") {
          val gen = Gen.elements(
            !!,
            !! / "a",
            !! / "a" / "b",
            !! / "a" / "b" / "c",
            !! / "a" / "b" / "c" / "",
            !! / "a" / "b" / "c" / "" / "",
            !! / "a" / "b" / "c" / "" / "" / "",
            "a" /: !!,
            "a" /: "b" /: !!,
            "a" /: "b" /: "c" /: !!,
            "a" /: "b" /: "c" /: "" /: !!,
            "a" /: "b" /: "c" /: "" /: "" /: !!,
            "a" /: "b" /: "c" /: "" /: "" /: "" /: !!,
          )

          checkAll(gen) { path =>
            val actual   = path.encode
            val expected = Path.decode(actual).encode
            assertTrue(actual == expected)
          }
        },
      ),
      suite("decode")(
        suite("trailingSlash")(
          testM("isTrue") {
            val gen = Gen.elements(
              "a/",
              "a/b/",
              "a/b/c/",
              "/a/",
              "/a/b/",
              "/a/b/c/",
            )

            checkAll(gen) { path =>
              val actual = Path.decode(path)
              assertTrue(actual.trailingSlash)
            }
          },
          testM("isFalse") {
            val gen = Gen.elements(
              "",
              "//",
              "a",
              "a/b",
              "a/b/c",
              "/a",
              "/a/b",
              "/a/b/c",
            )

            checkAll(gen) { path =>
              val actual = Path.decode(path)
              assertTrue(!actual.trailingSlash)
            }
          },
        ),
      ),
    )
}
