package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object PathSpec extends DefaultRunnableSpec with HExitAssertion {
  def collect[A](pf: PartialFunction[Path, A]): String => Option[A] = path => pf.lift(Path.decode(path))

  def spec = {
    suite("Path")(
      suite("Syntax")(
        suite("/")(
          testM("isDefined") {
            val gen = Gen.fromIterable(
              Seq(
                // Exact
                collect { case Path.root => true }                   -> "",
                collect { case Path.root / "a" => true }             -> "/a",
                collect { case Path.root / "a" / "b" => true }       -> "/a/b",
                collect { case Path.root / "a" / "b" / "c" => true } -> "/a/b/c",

                // Wildcards
                collect { case Path.root / _ => true }         -> "/a",
                collect { case Path.root / _ / _ => true }     -> "/a/b",
                collect { case Path.root / _ / _ / _ => true } -> "/a/b/c",

                // Wildcard mix
                collect { case _ / "c" => true }     -> "/a/b/c",
                collect { case _ / _ / "c" => true } -> "/a/b/c",

                // Trailing Slash
                collect { case Path.root / "a" / "" => true }       -> "/a/",
                collect { case Path.root / "a" / "b" / "" => true } -> "/a/b/",
                collect { case Path.root / "a" / "" / "" => true }  -> "/a//",

                // Leading Slash
                collect { case Path.root / "" => true } -> "/",
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isDefined)
            }
          },
          testM("isEmpty") {
            val gen = Gen.fromIterable(
              Seq(
                collect { case Path.root => true }                   -> "/a",
                collect { case Path.root / "a" => true }             -> "/b",
                collect { case Path.root / "a" / "b" => true }       -> "/a",
                collect { case Path.root / "a" / "b" / "c" => true } -> "/a/b",
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isEmpty)
            }
          },
        ),
        suite("/:")(
          testM("isDefined") {
            val gen = Gen.fromIterable(
              Seq(
                // Exact
                collect { case "a" /: Path.root => true }               -> "a/",
                collect { case "a" /: "b" /: Path.root => true }        -> "a/b/",
                collect { case "a" /: "b" /: "c" /: Path.root => true } -> "a/b/c/",

                // Wildcard
                collect { case "a" /: _ => true }        -> "a",
                collect { case "a" /: "b" /: _ => true } -> "a/b/c",
                collect { case "a" /: _ /: _ => true }   -> "a/b/c",
                collect { case "a" /: _ => true }        -> "a/b/c",

                //
                collect { case "a" /: "b" /: "c" /: _ => true } -> "a/b/c",
                collect { case "a" /: "b" /: _ /: _ => true }   -> "a/b/c",
                collect { case "a" /: _ /: _ /: _ => true }     -> "a/b/c",
                collect { case _ /: _ /: _ /: _ => true }       -> "/a/b/c",
                collect { case _ /: _ /: _ => true }            -> "/a/b/c",
                collect { case _ /: _ => true }                 -> "/a/b/c",

                // Trailing slash
                collect { case "a" /: Path.root => true }               -> "a/",
                collect { case "a" /: "b" /: Path.root => true }        -> "a/b/",
                collect { case "a" /: "b" /: "c" /: Path.root => true } -> "a/b/c/",
                collect { case "a" /: "" /: Path.root => true }         -> "a//",

                // Leading Slash
                collect { case "" /: Path.root => true }                       -> "/",
                collect { case "" /: "a" /: Path.empty => true }               -> "/a",
                collect { case "" /: "a" /: "b" /: Path.empty => true }        -> "/a/b",
                collect { case "" /: "a" /: "b" /: "c" /: Path.empty => true } -> "/a/b/c",
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isDefined)
            }
          },
          testM("isEmpty") {
            val gen = Gen.fromIterable(
              Seq(
                collect { case "a" /: Path.root => true }               -> "/b",
                collect { case "a" /: "b" /: Path.root => true }        -> "a",
                collect { case "a" /: "b" /: "c" /: Path.root => true } -> "a/b",
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isEmpty)
            }
          },
        ),
        suite("int()")(
          test("extract path 'user' /: int(1)") {
            val path = collect { case "user" /: int(age) /: Path.root => age } { "user/1/" }
            assert(path)(isSome(equalTo(1)))
          },
          test("extract path 'user' /: int(Xyz)") {
            val path = collect { case "user" /: int(age) /: Path.root => age } { "user/Xyz/" }
            assert(path)(isNone)
          },
        ),
        suite("boolean()")(
          test("extract path 'user' /: boolean(true)") {
            val path = collect { case "user" /: boolean(ok) /: Path.root => ok } { "user/True/" }
            assert(path)(isSome(isTrue))
          },
          test("extract path 'user' /: boolean(false)") {
            val path = collect { case "user" /: boolean(ok) /: Path.root => ok } { "user/false/" }
            assert(path)(isSome(isFalse))
          },
        ),
      ),
      suite("startsWith")(
        testM("isTrue") {
          val gen = Gen.fromIterable(
            Seq(
              Path.root                   -> Path.root,
              Path.root / "a"             -> Path.root / "a",
              Path.root / "a" / "b"       -> Path.root / "a" / "b",
              Path.root / "a" / "b" / "c" -> Path.root / "a",
              Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
              Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
              Path.root / "a" / "b" / "c" -> Path.root / "a" / "b" / "c",
            ),
          )

          checkAll(gen) { case (path, expected) =>
            val actual = path.startsWith(expected)
            assertTrue(actual)
          }
        },
        testM("isFalse") {
          val gen = Gen.fromIterable(
            Seq(
              Path.root             -> Path.root / "a",
              Path.root / "a"       -> Path.root / "a" / "b",
              Path.root / "a"       -> Path.root / "b",
              Path.root / "a" / "b" -> Path.root / "a" / "b" / "c",
            ),
          )

          checkAll(gen) { case (path, expected) =>
            val actual = !path.startsWith(expected)
            assertTrue(actual)
          }
        },
      ),
      testM("take") {
        val gen = Gen.fromIterable(
          Seq(
            (1, Path.root)                   -> Path.root,
            (1, Path.root / "a")             -> Path.root,
            (1, Path.root / "a" / "b")       -> Path.root,
            (1, Path.root / "a" / "b" / "c") -> Path.root,
            (2, Path.root / "a" / "b" / "c") -> Path.root / "a",
            (3, Path.root / "a" / "b" / "c") -> Path.root / "a" / "b",
            (4, Path.root / "a" / "b" / "c") -> Path.root / "a" / "b" / "c",
          ),
        )

        checkAll(gen) { case ((n, path), expected) =>
          val actual = path.take(n)
          assertTrue(actual == expected)
        }
      },
      testM("drop") {
        val gen = Gen.fromIterable(
          Seq(
            (1, Path.root)                   -> Path.empty,
            (1, Path.root / "a")             -> Path.empty / "a",
            (1, Path.root / "a" / "b")       -> Path.empty / "a" / "b",
            (1, Path.root / "a" / "b" / "c") -> Path.empty / "a" / "b" / "c",
            (2, Path.root / "a" / "b" / "c") -> Path.empty / "b" / "c",
            (3, Path.root / "a" / "b" / "c") -> Path.empty / "c",
            (4, Path.root / "a" / "b" / "c") -> Path.empty,
          ),
        )

        checkAll(gen) { case ((n, path), expected) =>
          val actual = path.drop(n)
          assertTrue(actual == expected)
        }
      },
      testM("dropLast") {
        val gen = Gen.fromIterable(
          Seq(
            (1, Path.root)                   -> Path.empty,
            (1, Path.root / "a")             -> Path.root,
            (1, Path.root / "a" / "b")       -> Path.root / "a",
            (1, Path.root / "a" / "b" / "c") -> Path.root / "a" / "b",
            (2, Path.root / "a" / "b" / "c") -> Path.root / "a",
            (3, Path.root / "a" / "b" / "c") -> Path.root,
            (4, Path.root / "a" / "b" / "c") -> Path.empty,
          ),
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
            val expected = path
            val encoded  = path.encode
            val actual   = Path.decode(encoded)
            assertTrue(actual == expected)
          }
        },
        testM("string regression") {
          val gen = Gen.fromIterable(
            Seq(
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
            ),
          )

          checkAll(gen) { path =>
            val actual   = path
            val expected = Path.decode(actual).encode
            assertTrue(actual == expected)
          }
        },
        testM("path elements") {
          val gen = Gen.fromIterable(
            Seq(
              Path.root,
              Path.root / "a",
              Path.root / "a" / "b",
              Path.root / "a" / "b" / "c",
              Path.root / "a" / "b" / "c" / "",
              Path.root / "a" / "b" / "c" / "" / "",
              Path.root / "a" / "b" / "c" / "" / "" / "",
              "a" /: Path.root,
              "a" /: "b" /: Path.root,
              "a" /: "b" /: "c" /: Path.root,
              "a" /: "b" /: "c" /: "" /: Path.root,
              "a" /: "b" /: "c" /: "" /: "" /: Path.root,
              "a" /: "b" /: "c" /: "" /: "" /: "" /: Path.root,
            ),
          )

          checkAll(gen) { path =>
            val actual   = path.encode
            val expected = Path.decode(actual).encode
            assertTrue(actual == expected)
          }
        },
      ),
    )
  }
}
