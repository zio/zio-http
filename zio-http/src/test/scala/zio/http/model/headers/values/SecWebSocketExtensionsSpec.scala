package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

import zio.http.model.headers.values.SecWebSocketExtensions.{Extension, Extensions, Token}

object SecWebSocketExtensionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketExtensions suite")(
    test("SecWebSocketExtensions should be properly parsed for a valid string") {
      val probe = "permessage-deflate; client_max_window_bits"
      assertTrue(
        SecWebSocketExtensions.toSecWebSocketExtensions(probe) == SecWebSocketExtensions.Extensions(
          Chunk(
            Token(Chunk(Extension.TokenParam("permessage-deflate"), Extension.TokenParam("client_max_window_bits"))),
          ),
        ),
      )
    },
    test("SecWebSocketExtensions should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketExtensions.toSecWebSocketExtensions(probe) == SecWebSocketExtensions.InvalidExtensions)
    },
    test("SecWebSocketExtensions should properly render a valid string") {
      val probe = "permessage-deflate; client_max_window_bits"
      assertTrue(
        SecWebSocketExtensions.fromSecWebSocketExtensions(
          SecWebSocketExtensions.Extensions(
            Chunk(
              Token(Chunk(Extension.TokenParam("permessage-deflate"), Extension.TokenParam("client_max_window_bits"))),
            ),
          ),
        ) == probe,
      )
    },
    test("SecWebSocketExtensions should properly render an empty string") {
      val probe = ""
      assertTrue(
        SecWebSocketExtensions.fromSecWebSocketExtensions(SecWebSocketExtensions.InvalidExtensions) == probe,
      )
    },
    test("SecWebSocket should properly parse and render a complex extension") {
      val probe = "permessage-deflate; client_max_window_bits; server_max_window_bits=15, deflate-stream"
      assertTrue(
        SecWebSocketExtensions.fromSecWebSocketExtensions(
          SecWebSocketExtensions.toSecWebSocketExtensions(probe),
        ) == probe,
      )
    },
  )
}
