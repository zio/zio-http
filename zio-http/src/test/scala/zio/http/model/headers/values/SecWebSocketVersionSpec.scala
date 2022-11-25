package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object SecWebSocketVersionSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketVersion suite")(
    test("SecWebSocketVersion should be properly parsed for a valid string") {
      val probe = "13"
      assertTrue(SecWebSocketVersion.toSecWebSocketVersion(probe) == SecWebSocketVersion.Version(13))
    },
    test("SecWebSocketVersion should be properly parsed for an invalid string value") {
      val probe = "22"
      assertTrue(SecWebSocketVersion.toSecWebSocketVersion(probe) == SecWebSocketVersion.InvalidVersion)
    },
    test("SecWebSocketVersion should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketVersion.toSecWebSocketVersion(probe) == SecWebSocketVersion.InvalidVersion)
    },
    test("SecWebSocketVersion should properly render a valid string") {
      val probe = "13"
      assertTrue(SecWebSocketVersion.fromSecWebSocketVersion(SecWebSocketVersion.Version(13)) == probe)
    },
    test("SecWebSocketVersion should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketVersion.fromSecWebSocketVersion(SecWebSocketVersion.InvalidVersion) == probe)
    },
  )
}
