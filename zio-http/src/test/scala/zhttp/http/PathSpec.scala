package zhttp.http

import zio.test.Assertion._
import zio.test._

object PathSpec extends DefaultRunnableSpec {
  def spec =
    suite("Path")(
      suite("toList")(
        test("/")(assert(Path("").toList)(equalTo(Nil))),
        test("/A")(assert(Path("A").toList)(equalTo(List("A")))),
        test("/A/B%2FC")(assert(Path("A", "B%2FC").toList)(equalTo(List("A", "B%2FC")))),
        test("/A/B/C")(assert(Path("A", "B", "C").toList)(equalTo(List("A", "B", "C")))),
      ),
      suite("apply")(
        test("/")(assert(Path("/"))(equalTo(Root))),
        test("/A")(assert(Path("/A"))(equalTo(Path("A")))),
        test("/A/B%2FC")(assert(Path("/A/B%2FC"))(equalTo(Path("A", "B%2FC")))),
        test("/A/B/C")(assert(Path("/A/B/C"))(equalTo(Path("A", "B", "C")))),
      ),
      suite("unapplySeq")(
        test("/")((Path(): @unchecked) match { case Root => assertCompletes }),
        test("/")((Path(""): @unchecked) match { case Root => assertCompletes }),
        test("/A/B")((Path("A", "B"): @unchecked) match { case Root / x / y => assert((x, y))(equalTo(("A", "B"))) }),
        test("/A/B%2FC")((Path("A", "B%2FC"): @unchecked) match {
          case Root / x / y => assert((x, y))(equalTo(("A", "B%2FC")))
        }),
        test("/A/B/C") {
          (Path("A", "B", "C"): @unchecked) match { case Path(x, y, z) => assert((x, y, z))(equalTo(("A", "B", "C"))) }
        },
      ),
      suite("startsWith")(
        test("isTrue") {
          assert(Root / "a" / "b" / "c" / "d" startsWith Root / "a")(isTrue) &&
          assert(Root / "a" / "b" / "c" / "d" startsWith Root / "a" / "b")(isTrue) &&
          assert(Root / "a" / "b" / "c" / "d" startsWith Root / "a" / "b" / "c")(isTrue) &&
          assert(Root / "a" / "b" / "c" / "d" startsWith Root / "a" / "b" / "c" / "d")(isTrue)
        },
        test("isFalse") {
          assert(Root / "a" / "b" / "c" / "d" startsWith Root / "a" / "b" / "c" / "d" / "e")(isFalse) &&
          assert(Root / "a" / "b" / "c" startsWith Root / "a" / "b" / "c" / "d")(isFalse) &&
          assert(Root / "a" / "b" startsWith Root / "a" / "b" / "c")(isFalse) &&
          assert(Root / "a" startsWith Root / "a" / "b")(isFalse)
        },
        test("isFalse") {
          assert(Root / "abcd" startsWith Root / "a")(isFalse)
        },
      ),
    )
}
