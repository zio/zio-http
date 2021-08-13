package zhttp.http

import zio.test.Assertion.{equalTo, isFalse, isTrue}
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
        test("/a/b/c vs /a/b") {
          assert(Root / "a" / "b" / "c" startsWith Root / "a" / "b")(isTrue)
        },
        test("/a/b/c vs /a/b/c") {
          assert(Root / "a" / "b" / "c" startsWith Root / "a" / "b" / "c")(isTrue)
        },
        test("/a/b vs /a/b/c") {
          assert(Root / "a" / "b" startsWith Root / "a" / "b" / "c")(isFalse)
        },
      ),
    )
}
