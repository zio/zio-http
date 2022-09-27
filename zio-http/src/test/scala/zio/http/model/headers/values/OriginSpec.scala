package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.http.model.headers.values.Origin.{InvalidOriginValue, OriginNull, OriginValue}
import zio.http.{Path, QueryParams}
import zio.test._

object OriginSpec extends ZIOSpecDefault {
  override def spec = suite("Origin header suite")(
    test("Origin: null") {
      assertTrue(Origin.toOrigin("null") == OriginNull) &&
      assertTrue(Origin.fromOrigin(OriginNull) == "null")
    },
    test("parsing of invalid Origin values") {
      assertTrue(Origin.toOrigin("") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("://host") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("http://:") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("http://:80") == InvalidOriginValue) &&
      assertTrue(Origin.toOrigin("host:80") == InvalidOriginValue)
    },
    test("parsing of valid Origin values") {
      check(HttpGen.genAbsoluteURL) { url =>
        val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
        assertTrue(
          Origin.toOrigin(justSchemeHostAndPort.encode) == OriginValue(
            url.scheme.map(_.encode).getOrElse(""),
            url.host.getOrElse(""),
            url.port,
          ),
        )
      }
    },
    test("parsing and encoding is symmetrical") {
      check(HttpGen.genAbsoluteURL) { url =>
        val justSchemeHostAndPort = url.copy(path = Path.empty, queryParams = QueryParams.empty, fragment = None)
        assertTrue(Origin.fromOrigin(Origin.toOrigin(justSchemeHostAndPort.encode)) == justSchemeHostAndPort.encode)
      }
    },
  )
}
