package zio.http.model.headers.values

import zio.http.model.Method
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object AccessControlAllowMethodsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowMethods suite")(
    test("AccessControlAllowMethods should be parsed correctly") {
      val accessControlAllowMethods       = AccessControlAllowMethods.AllowMethods(
        Chunk(
          Method.GET,
          Method.POST,
          Method.PUT,
          Method.DELETE,
          Method.HEAD,
          Method.OPTIONS,
          Method.PATCH,
        ),
      )
      val accessControlAllowMethodsString = "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH"
      assertTrue(
        AccessControlAllowMethods
          .toAccessControlAllowMethods(accessControlAllowMethodsString) == accessControlAllowMethods,
      )
    },
    test("AccessControlAllowMethods should be parsed correctly when * is used") {
      val accessControlAllowMethods       = AccessControlAllowMethods.AllowAllMethods
      val accessControlAllowMethodsString = "*"
      assertTrue(
        AccessControlAllowMethods
          .toAccessControlAllowMethods(accessControlAllowMethodsString) == accessControlAllowMethods,
      )
    },
    test("AccessControlAllowMethods should be parsed correctly when empty string is used") {
      val accessControlAllowMethods       = AccessControlAllowMethods.NoMethodsAllowed
      val accessControlAllowMethodsString = ""
      assertTrue(
        AccessControlAllowMethods
          .toAccessControlAllowMethods(accessControlAllowMethodsString) == accessControlAllowMethods,
      )
    },
    test("AccessControlAllowMethods should properly render NoMethodsAllowed value") {
      assertTrue(
        AccessControlAllowMethods.fromAccessControlAllowMethods(AccessControlAllowMethods.NoMethodsAllowed) == "",
      )
    },
    test("AccessControlAllowMethods should properly render AllowAllMethods value") {
      assertTrue(
        AccessControlAllowMethods.fromAccessControlAllowMethods(AccessControlAllowMethods.AllowAllMethods) == "*",
      )
    },
    test("AccessControlAllowMethods should properly render AllowMethods value") {
      val accessControlAllowMethods       = AccessControlAllowMethods.AllowMethods(
        Chunk(
          Method.GET,
          Method.POST,
          Method.PUT,
          Method.DELETE,
          Method.HEAD,
          Method.OPTIONS,
          Method.PATCH,
        ),
      )
      val accessControlAllowMethodsString = "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH"
      assertTrue(
        AccessControlAllowMethods.fromAccessControlAllowMethods(
          accessControlAllowMethods,
        ) == accessControlAllowMethodsString,
      )
    },
  )
}
