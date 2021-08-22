package zhttp.http

import zio.test.Assertion.{equalTo, isFalse, isNone, isSome, isTrue}
import zio.test._

object PathSpec extends DefaultRunnableSpec with HttpResultAssertion {
  def collect[A](pf: PartialFunction[Path, A]): Path => Option[A] = path => pf.lift(path)
  def spec                                                        =
    suite("Path")(
      suite("toList")(
        test("empty")(assert(Path().toList)(equalTo(Nil))),
        test("empty string")(assert(Path("").toList)(equalTo(Nil))),
        test("just /")(assert(Path("/").toList)(equalTo(Nil))),
        test("un-prefixed")(assert(Path("A").toList)(equalTo(List("A")))),
        test("prefixed")(assert(Path("/A").toList)(equalTo(List("A")))),
        test("encoding string")(assert(Path("A", "B%2FC").toList)(equalTo(List("A", "B%2FC")))),
        test("nested paths")(assert(Path("A", "B", "C").toList)(equalTo(List("A", "B", "C")))),
      ),
      suite("apply()")(
        test("empty")(assert(Path())(equalTo(!!))),
        test("empty string")(assert(Path(""))(equalTo(!!))),
        test("just /")(assert(Path("/"))(equalTo(!!))),
        test("prefixed path")(assert(Path("/A"))(equalTo(Path("A")))),
        test("encoded paths")(assert(Path("/A/B%2FC"))(equalTo(Path("A", "B%2FC")))),
        test("nested paths")(assert(Path("/A/B/C"))(equalTo(Path("A", "B", "C")))),
      ),
      suite("asString")(
        test("a, b, c") {
          val path = Path("a", "b", "c").asString
          assert(path)(equalTo("/a/b/c"))
        },
        test("Path()") {
          val path = Path().asString
          assert(path)(equalTo(""))
        },
      ),
      suite("PathSyntax /:")(
        suite("default")(
          test("extract path 'name' /: name") {
            val path = collect { case "name" /: name => name.asString }
            assert(path(Path("name", "a", "b", "c")))(isSome(equalTo("/a/b/c")))
          },
          test("extract paths 'name' /: a /: b /: 'c' /: !!") {
            val path = collect { case "name" /: a /: b /: "c" /: !! => (a, b) }
            assert(path(Path("name", "a", "b", "c")))(isSome(equalTo(("a", "b"))))
          },
          test("extract paths 'name' /: a /: b /: _") {
            val path = collect { case "name" /: a /: b /: _ => (a, b) }
            assert(path(Path("name", "a", "b", "c")))(isSome(equalTo(("a", "b"))))
          },
          test("extract paths 'name' /: name /: 'a' /: 'b' /: 'c' /: !!") {
            val path = collect { case "name" /: name /: "a" /: "b" /: "c" /: !! => name.toString }
            assert(path(Path("name", "Xyz", "a", "b", "c")))(isSome(equalTo("Xyz")))
          },
        ),
        suite("int()")(
          test("extract path 'user' /: int(1)") {
            val path = collect { case "user" /: int(age) /: !! => age }
            assert(path(Path("user", "1")))(isSome(equalTo(1)))
          },
          test("extract path 'user' /: int(Xyz)") {
            val path = collect { case "user" /: int(age) /: !! => age }
            assert(path(Path("user", "Xyz")))(isNone)
          },
        ),
        suite("boolean()")(
          test("extract path 'user' /: boolean(true)") {
            val path = collect { case "user" /: boolean(ok) /: !! => ok }
            assert(path(Path("user", "True")))(isSome(isTrue))
          },
          test("extract path 'user' /: boolean(false)") {
            val path = collect { case "user" /: boolean(ok) /: !! => ok }
            assert(path(Path("user", "false")))(isSome(isFalse))
          },
        ),
      ),
    )
}
