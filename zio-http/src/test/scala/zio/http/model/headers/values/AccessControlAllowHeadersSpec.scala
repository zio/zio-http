package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{Chunk, Scope}

import zio.http.internal.HttpGen

object AccessControlAllowHeadersSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowHeaders suite")(
    test("AccessControlAllowHeaders should be parsed correctly for *") {
      assertTrue(
        AccessControlAllowHeaders.toAccessControlAllowHeaders("*") == AccessControlAllowHeaders.All,
      )
    },
    test("AccessControlAllowHeaders should be rendered correctly for *") {
      assertTrue(
        AccessControlAllowHeaders.fromAccessControlAllowHeaders(AccessControlAllowHeaders.All) == "*",
      )
    },
    test("AccessControlAllowHeaders should be parsed correctly for valid header names") {
      check(HttpGen.headerNames) { headerNames =>
        val headerNamesString = headerNames.mkString(", ")
        if (headerNamesString.isEmpty)
          assertTrue(
            AccessControlAllowHeaders.toAccessControlAllowHeaders(
              headerNamesString,
            ) == AccessControlAllowHeaders.NoHeaders,
          )
        else
          assertTrue(
            AccessControlAllowHeaders.toAccessControlAllowHeaders(headerNamesString) == AccessControlAllowHeaders
              .AccessControlAllowHeadersValue(
                Chunk.fromIterable(headerNames),
              ),
          )
      }
    },
  )
}
