package zio.http.model.headers.values

import zio.Scope
import zio.http.model.headers.values.ETag.{InvalidETagValue, StrongETagValue, WeakETagValue}
import zio.test._

object ETagSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ETag header suite")(
    test("parse ETag header") {
      assertTrue(ETag.toETag("""W/"testEtag"""") == WeakETagValue("testEtag"))
      assertTrue(ETag.toETag("""w/"testEtag"""") == WeakETagValue("testEtag"))
      assertTrue(ETag.toETag(""""testEtag"""") == StrongETagValue("testEtag"))
      assertTrue(ETag.toETag("W/Etag") == InvalidETagValue)
      assertTrue(ETag.toETag("Etag") == InvalidETagValue)
      assertTrue(ETag.toETag("""W/""""") == WeakETagValue(""))
      assertTrue(ETag.toETag("""""""") == StrongETagValue(""))
    },
    test("encode ETag header to String") {
      assertTrue(ETag.fromETag(StrongETagValue("TestEtag")) == """"TestEtag"""")
      assertTrue(ETag.fromETag(WeakETagValue("TestEtag")) == """W/"TestEtag"""")
      assertTrue(ETag.fromETag(InvalidETagValue) == "")
    },
    test("parsing and encoding are symmetrical") {
      assertTrue(ETag.fromETag(ETag.toETag("""w/"testEtag"""")) == """W/"testEtag"""")
      assertTrue(ETag.fromETag(ETag.toETag("""W/"testEtag"""")) == """W/"testEtag"""")
      assertTrue(ETag.fromETag(ETag.toETag(""""testEtag"""")) == """"testEtag"""")

    },
  )
}
