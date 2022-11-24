package zio.http.model.headers.values

import zio.{Chunk, Scope}
import zio.http.model.headers.values.Te.{DeflateEncoding, GZipEncoding, MultipleEncodings, Trailers}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}

object TeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TE suite")(
    test("parse TE header") {
      val te = "trailers, deflate;q=0.5, gzip;q=0.2"
      assertTrue(
        Te.toTe(te) ==
          MultipleEncodings(Chunk(Trailers, DeflateEncoding(Some(0.5)), GZipEncoding(Some(0.2)))),
      )
    },
    test("parse TE header - simple value with weight") {
      val te = "deflate;q=0.5"
      assertTrue(
        Te.toTe(te) ==
          DeflateEncoding(Some(0.5)),
      )
    },
    test("parse TE header - simple value") {
      val te = "trailers"
      assertTrue(
        Te.toTe(te) ==
          Trailers,
      )
    },
    test("render TE header") {
      val te = "trailers, deflate;q=0.5, gzip;q=0.2"
      assertTrue(
        Te.fromTe(Te.toTe(te)) == te,
      )
    },
  )
}
