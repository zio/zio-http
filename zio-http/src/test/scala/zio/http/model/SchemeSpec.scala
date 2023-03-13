package zio.http.model

import zio.test.Assertion.isNone
import zio.test._

import zio.http.internal.HttpGen

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme

object SchemeSpec extends ZIOSpecDefault {
  override def spec = suite("SchemeSpec")(
    test("string decode") {
      checkAll(HttpGen.scheme) { scheme =>
        assertTrue(Scheme.decode(scheme.encode).get == scheme)
      }
    },
    test("null string decode") {
      assert(Scheme.decode(null))(isNone)
    },
  )
}
