package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object AccessControlExposeHeadersSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlExposeHeaders suite")(
    test("AccessControlExposeHeaders should be parsed correctly") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.AccessControlExposeHeadersValue(
        Chunk(
          "X-Header1",
          "X-Header2",
          "X-Header3",
        ),
      )
      val accessControlExposeHeadersString = "X-Header1, X-Header2, X-Header3"
      assertTrue(
        AccessControlExposeHeaders
          .toAccessControlExposeHeaders(accessControlExposeHeadersString) == accessControlExposeHeaders,
      )
    },
    test("AccessControlExposeHeaders should be parsed correctly when * is used") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.All
      val accessControlExposeHeadersString = "*"
      assertTrue(
        AccessControlExposeHeaders
          .toAccessControlExposeHeaders(accessControlExposeHeadersString) == accessControlExposeHeaders,
      )
    },
    test("AccessControlExposeHeaders should be parsed correctly when empty string is used") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.NoHeaders
      val accessControlExposeHeadersString = ""
      assertTrue(
        AccessControlExposeHeaders
          .toAccessControlExposeHeaders(accessControlExposeHeadersString) == accessControlExposeHeaders,
      )
    },
    test("AccessControlExposeHeaders should properly render NoHeadersAllowed value") {
      assertTrue(
        AccessControlExposeHeaders.fromAccessControlExposeHeaders(AccessControlExposeHeaders.NoHeaders) == "",
      )
    },
    test("AccessControlExposeHeaders should properly render AllowAllHeaders value") {
      assertTrue(
        AccessControlExposeHeaders.fromAccessControlExposeHeaders(AccessControlExposeHeaders.All) == "*",
      )
    },
    test("AccessControlExposeHeaders should properly render AllowHeaders value") {
      val accessControlExposeHeaders       = AccessControlExposeHeaders.AccessControlExposeHeadersValue(
        Chunk(
          "X-Header1",
          "X-Header2",
          "X-Header3",
        ),
      )
      val accessControlExposeHeadersString = "X-Header1, X-Header2, X-Header3"
      assertTrue(
        AccessControlExposeHeaders.fromAccessControlExposeHeaders(
          accessControlExposeHeaders,
        ) == accessControlExposeHeadersString,
      )
    },
  )
}
