package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object SecWebSocketKeySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketKey suite")(
    test("SecWebSocketKey should be properly parsed for a valid string") {
      val probe = "dGhlIHNhbXBsZSBub25jZQ=="
      assertTrue(SecWebSocketKey.toSecWebSocketKey(probe) == SecWebSocketKey.Base64EncodedKey(probe))
    },
    test("SecWebSocketKey should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketKey.toSecWebSocketKey(probe) == SecWebSocketKey.InvalidKey)
    },
    test("SecWebSocketKey should properly render a valid string") {
      val probe = "dGhlIHNhbXBsZSBub25jZQ=="
      assertTrue(SecWebSocketKey.fromSecWebSocketKey(SecWebSocketKey.Base64EncodedKey(probe)) == probe)
    },
    test("SecWebSocketKey should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketKey.fromSecWebSocketKey(SecWebSocketKey.InvalidKey) == probe)
    },
  )
}
