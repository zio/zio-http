package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Origin.{InvalidOriginValue, OriginNull, OriginValue}
import zio.http.{Path, QueryParams}
import zio.test._

object HostSpec extends ZIOSpecDefault {
  override def spec = suite("Host header suite")(
    test("parsing of invalid Host values") {
      assertTrue(Host.toHost("") == Host.InvalidHostValue) &&
      assertTrue(Host.toHost(":bad") == Host.InvalidHostValue) &&
      assertTrue(Host.toHost("bad:") == Host.InvalidHostValue) &&
      assertTrue(Host.toHost("host:badport") == Host.InvalidHostValue)
      assertTrue(Host.toHost(":") == Host.InvalidHostValue)
    },
    test("parsing of valid Host values") {
      check(HttpGen.genAbsoluteURL) { url =>
        val hostString = (url.host.getOrElse("") + url.port.fold("")(p => s":$p"))
        assertTrue(
          Host.toHost(hostString) == Host.HostValue(
            url.host.getOrElse(""),
            url.port,
          ),
        )
      }
    },
    test("parsing and encoding is symmetrical") {
      check(HttpGen.genAbsoluteURL) { url =>
        val hostString = (url.host.getOrElse("") + url.port.fold("")(p => s":$p"))
        assertTrue(Host.fromHost(Host.toHost(hostString)) == hostString)
      }
    },
  )
}
