package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object SecWebSocketProtocolSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketProtocol suite")(
    test("SecWebSocketProtocol should be properly parsed for a valid string") {
      val probe = "chat, superchat"
      assertTrue(
        SecWebSocketProtocol.toSecWebSocketProtocol(probe) == SecWebSocketProtocol.Protocols(
          Chunk.fromArray(probe.split(", ")),
        ),
      )
    },
    test("SecWebSocketProtocol should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketProtocol.toSecWebSocketProtocol(probe) == SecWebSocketProtocol.InvalidProtocol)
    },
    test("SecWebSocketProtocol should properly render a valid string") {
      val probe = "chat, superchat"
      assertTrue(
        SecWebSocketProtocol.fromSecWebSocketProtocol(
          SecWebSocketProtocol.Protocols(Chunk.fromArray(probe.split(", "))),
        ) == probe,
      )
    },
    test("SecWebSocketProtocol should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketProtocol.fromSecWebSocketProtocol(SecWebSocketProtocol.InvalidProtocol) == probe)
    },
  )
}
