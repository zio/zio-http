package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object IfMatchSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfMatch suite")(
    test("IfMatch '*' should be parsed correctly") {
      val ifMatch = IfMatch.toIfMatch("*")
      assertTrue(ifMatch == IfMatch.Any)
    },
    test("IfMatch '' should be parsed correctly") {
      val ifMatch = IfMatch.toIfMatch("")
      assertTrue(ifMatch == IfMatch.None)
    },
    test("IfMatch 'etag1, etag2' should be parsed correctly") {
      val ifMatch = IfMatch.toIfMatch("etag1, etag2")
      assertTrue(ifMatch == IfMatch.ETags(Chunk("etag1", "etag2")))
    },
    test("IfMatch 'etag1, etag2' should be rendered correctly") {
      val ifMatch = IfMatch.ETags(Chunk("etag1", "etag2"))
      assertTrue(IfMatch.fromIfMatch(ifMatch) == "etag1,etag2")
    },
  )
}
