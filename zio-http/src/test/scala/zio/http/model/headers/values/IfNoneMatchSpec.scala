package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object IfNoneMatchSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("IfNoneMatch suite")(
    test("IfMatch '*' should be parsed correctly") {
      val ifMatch = IfNoneMatch.toIfNoneMatch("*")
      assertTrue(ifMatch == IfNoneMatch.Any)
    },
    test("IfMatch '' should be parsed correctly") {
      val ifMatch = IfNoneMatch.toIfNoneMatch("")
      assertTrue(ifMatch == IfNoneMatch.None)
    },
    test("IfMatch 'etag1, etag2' should be parsed correctly") {
      val ifMatch = IfNoneMatch.toIfNoneMatch("etag1, etag2")
      assertTrue(ifMatch == IfNoneMatch.ETags(Chunk("etag1", "etag2")))
    },
    test("IfMatch 'etag1, etag2' should be rendered correctly") {
      val ifMatch = IfNoneMatch.ETags(Chunk("etag1", "etag2"))
      assertTrue(IfNoneMatch.fromIfNoneMatch(ifMatch) == "etag1,etag2")
    },
  )
}
