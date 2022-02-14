package zhttp.http

import zio.test.Assertion._
import zio.test._

object PathSpec extends DefaultRunnableSpec with HExitAssertion {
  def collect[A](pf: PartialFunction[Path, A]): Path => Option[A] = path => pf.lift(path)
  def spec                                                        =
    suite("Path")(
      suite("toList")(
        test("empty")(assert(Path().toList)(equalTo(Nil))) +
          test("empty string")(assert(Path("").toList)(equalTo(Nil))) +
          test("just /")(assert(Path("/").toList)(equalTo(Nil))) +
          test("un-prefixed")(assert(Path("A").toList)(equalTo(List("A")))) +
          test("prefixed")(assert(Path("/A").toList)(equalTo(List("A")))) +
          test("nested paths")(assert(Path("A", "B", "C").toList)(equalTo(List("A", "B", "C")))) +
          test("encoding string")(assert(Path("A", "B%2FC").toList)(equalTo(List("A", "B%2FC")))),
      ) +
        suite("apply()")(
          test("empty")(assert(Path())(equalTo(!!))) +
            test("empty string")(assert(Path(""))(equalTo(!!))) +
            test("just /")(assert(Path("/"))(equalTo(!!))) +
            test("prefixed path")(assert(Path("/A"))(equalTo(Path("A")))) +
            test("encoded paths")(assert(Path("/A/B%2FC"))(equalTo(Path("A", "B%2FC")))) +
            test("nested paths")(assert(Path("/A/B/C"))(equalTo(Path("A", "B", "C")))),
        ) +
        suite("unapplySeq")(
          test("a, b, c") {
            val path = collect { case Path(a, b, c) => (a, b, c) }
            assert(path(Path("a", "b", "c")))(isSome(equalTo(("a", "b", "c"))))
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
        test("take") {
          assert(!! / "a" / "b" / "c" take 1)(equalTo(!! / "a")) &&
          assert(!! take 1)(equalTo(!!))
        },
    )
}
