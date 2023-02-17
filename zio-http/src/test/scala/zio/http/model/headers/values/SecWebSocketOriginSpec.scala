package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.URL

object SecWebSocketOriginSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketOrigin suite")(
    test("SecWebSocketOrigin should be properly parsed for a valid string") {
      val probe    = "wss://example.com"
      val probeURL = URL.fromString(probe).fold(_ => URL.empty, url => url)
      assertTrue(SecWebSocketOrigin.toSecWebSocketOrigin(probe) == SecWebSocketOrigin.OriginValue(probeURL))
    },
    test("SecWebSocketOrigin should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketOrigin.toSecWebSocketOrigin(probe) == SecWebSocketOrigin.EmptyOrigin)
    },
    test("SecWebSocketOrigin should properly render a valid string") {
      val probe    = "wss://example.com"
      val probeURL = URL.fromString(probe).fold(_ => URL.empty, url => url)
      assertTrue(SecWebSocketOrigin.fromSecWebSocketOrigin(SecWebSocketOrigin.OriginValue(probeURL)) == probe)
    },
    test("SecWebSocketOrigin should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketOrigin.fromSecWebSocketOrigin(SecWebSocketOrigin.EmptyOrigin) == probe)
    },
  )
}
