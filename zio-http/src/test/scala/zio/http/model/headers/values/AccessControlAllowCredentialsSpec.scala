package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object AccessControlAllowCredentialsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowCredentials suite")(
    test("AccessControlAllowCredentials should be parsed correctly for true") {
      assertTrue(
        AccessControlAllowCredentials.toAccessControlAllowCredentials(
          "true",
        ) == AccessControlAllowCredentials.AllowCredentials,
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for false") {
      assertTrue(
        AccessControlAllowCredentials.toAccessControlAllowCredentials(
          "false",
        ) == AccessControlAllowCredentials.DoNotAllowCredentials,
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for invalid string") {
      assertTrue(
        AccessControlAllowCredentials.toAccessControlAllowCredentials(
          "some dummy string",
        ) == AccessControlAllowCredentials.DoNotAllowCredentials,
      )
    },
    test("AccessControlAllowCredentials should be parsed correctly for empty string") {
      assertTrue(
        AccessControlAllowCredentials.toAccessControlAllowCredentials(
          "",
        ) == AccessControlAllowCredentials.DoNotAllowCredentials,
      )
    },
    test("AccessControlAllowCredentials should be rendered correctly to false") {
      assertTrue(
        AccessControlAllowCredentials.fromAccessControlAllowCredentials(
          AccessControlAllowCredentials.DoNotAllowCredentials,
        ) == "false",
      )
    },
    test("AccessControlAllowCredentials should be rendered correctly to true") {
      assertTrue(
        AccessControlAllowCredentials.fromAccessControlAllowCredentials(
          AccessControlAllowCredentials.AllowCredentials,
        ) == "true",
      )
    },
  )
}
