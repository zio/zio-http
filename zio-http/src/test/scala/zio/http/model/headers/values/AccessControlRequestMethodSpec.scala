package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.model.Method

object AccessControlRequestMethodSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlRequestMethodSpec")(
    test("AccessControlRequestMethod.toAccessControlRequestMethod") {
      val method = "connect"
      assertTrue(
        AccessControlRequestMethod.toAccessControlRequestMethod(method) == AccessControlRequestMethod.RequestMethod(
          Method.CONNECT,
        ),
      )
    },
    test("AccessControlRequestMethod.fromAccessControlRequestMethod") {
      val method = Method.CONNECT
      assertTrue(
        AccessControlRequestMethod.fromAccessControlRequestMethod(
          AccessControlRequestMethod.RequestMethod(method),
        ) == method.text,
      )
    },
    test("AccessControlRequestMethod.toAccessControlRequestMethod invalid input") {
      val method = "some dummy data"
      assertTrue(
        AccessControlRequestMethod.toAccessControlRequestMethod(method) == AccessControlRequestMethod.InvalidMethod,
      )
    },
    test("AccessControlRequestMethod.toAccessControlRequestMethod empty input") {
      val method = ""
      assertTrue(
        AccessControlRequestMethod.toAccessControlRequestMethod(method) == AccessControlRequestMethod.InvalidMethod,
      )
    },
  )
}
