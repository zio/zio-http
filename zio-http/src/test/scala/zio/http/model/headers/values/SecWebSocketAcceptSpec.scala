package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object SecWebSocketAcceptSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketAccept suite")(
    test("SecWebSocketAccept should be properly parsed for a valid string") {
      val probe = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
      assertTrue(SecWebSocketAccept.toSecWebSocketAccept(probe) == SecWebSocketAccept.HashedKey(probe))
    },
    test("SecWebSocketAccept should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketAccept.toSecWebSocketAccept(probe) == SecWebSocketAccept.InvalidHashedKey)
    },
    test("SecWebSocketAccept should properly render a valid string") {
      val probe = "s3pPLMBiTxaQ9kYGzzhZRbK+xOo="
      assertTrue(SecWebSocketAccept.fromSecWebSocketAccept(SecWebSocketAccept.HashedKey(probe)) == probe)
    },
    test("SecWebSocketAccept should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketAccept.fromSecWebSocketAccept(SecWebSocketAccept.InvalidHashedKey) == probe)
    },
  )
}
