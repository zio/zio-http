package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

import zio.http.URL

object SecWebSocketLocationSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketLocation suite")(
    test("SecWebSocketLocation should be properly parsed for a valid string") {
      val probe    = "ws://example.com"
      val probeURL = URL.fromString(probe).fold(_ => URL.empty, url => url)
      assertTrue(SecWebSocketLocation.toSecWebSocketLocation(probe) == SecWebSocketLocation.LocationValue(probeURL))
    },
    test("SecWebSocketLocation should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketLocation.toSecWebSocketLocation(probe) == SecWebSocketLocation.EmptyLocationValue)
    },
    test("SecWebSocketLocation should properly render a valid string") {
      val probe    = "ws://example.com"
      val probeURL = URL.fromString(probe).fold(_ => URL.empty, url => url)
      assertTrue(SecWebSocketLocation.fromSecWebSocketLocation(SecWebSocketLocation.LocationValue(probeURL)) == probe)
    },
    test("SecWebSocketLocation should properly render an empty string") {
      val probe = ""
      assertTrue(SecWebSocketLocation.fromSecWebSocketLocation(SecWebSocketLocation.EmptyLocationValue) == probe)
    },
  )
}
