package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

import zio.http.model.headers.values.AccessControlRequestHeaders.fromAccessControlRequestHeaders

object AccessControlRequestHeadersSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlRequestHeaders suite")(
    test("AccessControlRequestHeadersValue") {
      val values                      = Chunk.fromIterable(List("a", "b", "c"))
      val accessControlRequestHeaders = AccessControlRequestHeaders.toAccessControlRequestHeaders(values.mkString(","))
      assertTrue(fromAccessControlRequestHeaders(accessControlRequestHeaders) == values.mkString(","))
    },
    test("NoRequestHeaders") {
      val accessControlRequestHeaders = AccessControlRequestHeaders.toAccessControlRequestHeaders("")
      assertTrue(fromAccessControlRequestHeaders(accessControlRequestHeaders) == "")
    },
  )
}
