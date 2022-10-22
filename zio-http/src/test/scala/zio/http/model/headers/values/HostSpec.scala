package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Host
import zio.http.model.headers.values.Host.{HostValue, InvalidHostValue}
import zio.test._

object HostSpec extends ZIOSpecDefault {
  override def spec = suite("Host header suite")(
    test("Empty Host") {
      assertTrue(Host.toHost("") == Host.EmptyHostValue) &&
      assertTrue(Host.fromHost(Host.EmptyHostValue) == "")
    },
    test("parsing of valid Host values") {
      check(HttpGen.genAbsoluteLocation) { url =>
        assertTrue(Host.toHost(url.host) == HostValue(url.host))
        assertTrue(Host.toHost(s"${url.host}:${url.port}") == HostValue(url.host, url.port))
      }
    },
    test("parsing of invalid Host values") {
      assertTrue(Host.toHost("random.com:ds43") == InvalidHostValue)
      assertTrue(Host.toHost("random.com:ds43:4434") == InvalidHostValue)

    },
    test("parsing and encoding is symmetrical") {
      check(HttpGen.genAbsoluteLocation) { url =>
        assertTrue(Host.fromHost(Host.toHost("random.com:4ds")) == "")
        assertTrue(Host.fromHost(Host.toHost("")) == "")
        assertTrue(Host.fromHost(Host.toHost(url.host)) == url.host)
        assertTrue(Host.fromHost(Host.toHost(s"${url.host}:${url.port}")) == s"${url.host}:${url.port}")

      }
    },
  )
}
