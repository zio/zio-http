package zhttp.http

import zhttp.internal.HttpGen
import zio.test.Assertion._
import zio.test._

object PathSpec extends DefaultRunnableSpec with HExitAssertion {
  def collect[A](pf: PartialFunction[Path, A]): Path => Option[A] = path => pf.lift(path)

  def spec = {
    suite("Path")(
      suite("Syntax")(
        suite("/")(
          testM("isDefined") {

            val gen = Gen.fromIterable(
              Seq(
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

                // Trailing Slash
                collect { case !! / "a" / "" => true }      -> !! / "a" / "",
                collect { case !! / "a" / "" / "" => true } -> !! / "a" / "" / "",

                // Leading Slash
                collect { case !! / "" => true } -> !! / "",
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isDefined)
            }
          },
          testM("isEmpty") {
            val gen = Gen.fromIterable(
              Seq(
                collect { case !! => true }                   -> !! / "a",
                collect { case !! / "a" => true }             -> !! / "b",
                collect { case !! / "a" / "b" => true }       -> !! / "a",
                collect { case !! / "a" / "b" / "c" => true } -> !! / "a" / "b",
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
                collect { case "a" /: !! => true }               -> "a" /: !!,
                collect { case "a" /: "b" /: !! => true }        -> "a" /: "b" /: !!,
                collect { case "a" /: "b" /: "c" /: !! => true } -> "a" /: "b" /: "c" /: !!,

                // Wildcard
                collect { case "a" /: _ => true }        -> "a" /: !!,
                collect { case "a" /: "b" /: _ => true } -> "a" /: "b" /: !!,
                collect { case "a" /: _ /: _ => true }   -> "a" /: "b" /: !!,
                collect { case "a" /: _ => true }        -> "a" /: "b" /: !!,

                //
                collect { case "a" /: "b" /: "c" /: _ => true } -> "a" /: "b" /: "c" /: !!,
                collect { case "a" /: "b" /: _ /: _ => true }   -> "a" /: "b" /: "c" /: !!,
                collect { case "a" /: _ /: _ /: _ => true }     -> "a" /: "b" /: "c" /: !!,
                collect { case _ /: _ /: _ /: _ => true }       -> "a" /: "b" /: "c" /: !!,
                collect { case _ /: _ /: _ => true }            -> "a" /: "b" /: "c" /: !!,
                collect { case _ /: _ => true }                 -> "a" /: "b" /: "c" /: !!,

                // Trailing slash
                collect { case "a" /: "" /: !! => true }       -> "a" /: "" /: !!,
                collect { case "a" /: "" /: "" /: !! => true } -> "a" /: "" /: "" /: !!,

                // Leading Slash
                collect { case "" /: !! => true } -> "" /: !!,
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isDefined)
            }
          },
          testM("isEmpty") {
            val gen = Gen.fromIterable(
              Seq(
                collect { case "a" /: !! => true }               -> "b" /: !!,
                collect { case "a" /: "b" /: !! => true }        -> "a" /: !!,
                collect { case "a" /: "b" /: "c" /: !! => true } -> "a" /: "b" /: !!,
              ),
            )

            checkAll(gen) { case (pf, path) =>
              assertTrue(pf(path).isEmpty)
            }
          },
        ),
        suite("int()")(
          test("extract path 'user' /: int(1)") {
            val path = collect { case "user" /: int(age) /: !! => age } { Path.decode("user/1") }
            assert(path)(isSome(equalTo(1)))
          },
          test("extract path 'user' /: int(Xyz)") {
            val path = collect { case "user" /: int(age) /: !! => age } { Path.decode("user/Xyz") }
            assert(path)(isNone)
          },
        ),
        suite("boolean()")(
          test("extract path 'user' /: boolean(true)") {
            val path = collect { case "user" /: boolean(ok) /: !! => ok } { Path.decode("user/True") }
            assert(path)(isSome(isTrue))
          },
          test("extract path 'user' /: boolean(false)") {
            val path = collect { case "user" /: boolean(ok) /: !! => ok } { Path.decode("user/false") }
            assert(path)(isSome(isFalse))
          },
        ),
      ),
      suite("startsWith")(
        testM("isTrue") {
          val gen = Gen.fromIterable(
            Seq(
              !!                   -> !!,
              !! / "a"             -> !! / "a",
              !! / "a" / "b"       -> !! / "a" / "b",
              !! / "a" / "b" / "c" -> !! / "a",
              !! / "a" / "b" / "c" -> !! / "a" / "b" / "c",
              !! / "a" / "b" / "c" -> !! / "a" / "b" / "c",
              !! / "a" / "b" / "c" -> !! / "a" / "b" / "c",
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
              !!             -> !! / "a",
              !! / "a"       -> !! / "a" / "b",
              !! / "a"       -> !! / "b",
              !! / "a" / "b" -> !! / "a" / "b" / "c",
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
            (1, !!)                   -> !!,
            (1, !! / "a")             -> !! / "a",
            (1, !! / "a" / "b")       -> !! / "a",
            (1, !! / "a" / "b" / "c") -> !! / "a",
            (2, !! / "a" / "b" / "c") -> !! / "a" / "b",
            (3, !! / "a" / "b" / "c") -> !! / "a" / "b" / "c",
            (4, !! / "a" / "b" / "c") -> !! / "a" / "b" / "c",
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
            (1, !!)                   -> !!,
            (1, !! / "a")             -> !!,
            (1, !! / "a" / "b")       -> !! / "b",
            (1, !! / "a" / "b" / "c") -> !! / "b" / "c",
            (2, !! / "a" / "b" / "c") -> !! / "c",
            (3, !! / "a" / "b" / "c") -> !!,
            (4, !! / "a" / "b" / "c") -> !!,
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
            (1, !!)                   -> !!,
            (1, !! / "a")             -> !!,
            (1, !! / "a" / "b")       -> !! / "a",
            (1, !! / "a" / "b" / "c") -> !! / "a" / "b",
            (2, !! / "a" / "b" / "c") -> !! / "a",
            (3, !! / "a" / "b" / "c") -> !!,
            (4, !! / "a" / "b" / "c") -> !!,
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
            val expected = path                 // Path(Nil)
            val encoded  = path.encode          // ""
            val actual   = Path.decode(encoded) // Path()
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
