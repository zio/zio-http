package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object XFrameOptionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("XFrameOptions suite")(
    test("parsing of invalid XFrameOptions values") {
      assertTrue(XFrameOptions.toXFrameOptions("") == XFrameOptions.Invalid) &&
      assertTrue(XFrameOptions.toXFrameOptions("any string") == XFrameOptions.Invalid) &&
      assertTrue(XFrameOptions.toXFrameOptions("DENY ") == XFrameOptions.Deny) &&
      assertTrue(XFrameOptions.toXFrameOptions("SAMEORIGIN ") == XFrameOptions.SameOrigin)
    },
    test("rendering of XFrameOptions values") {
      assertTrue(XFrameOptions.fromXFrameOptions(XFrameOptions.Deny) == "DENY") &&
      assertTrue(XFrameOptions.fromXFrameOptions(XFrameOptions.SameOrigin) == "SAMEORIGIN") &&
      assertTrue(XFrameOptions.fromXFrameOptions(XFrameOptions.Invalid) == "")
    },
  )
}
