package zio.http.model

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme
import zio.http.internal.HttpGen
import zio.test.Assertion.isNone
import zio.test._

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
    test("java http scheme") {
      checkAll(jHttpScheme) { jHttpScheme =>
        assertTrue(Scheme.fromJScheme(jHttpScheme).flatMap(_.toJHttpScheme).get == jHttpScheme)
      }
    },
    test("java websocket scheme") {
      checkAll(jWebSocketScheme) { jWebSocketScheme =>
        assertTrue(
          Scheme.fromJScheme(jWebSocketScheme).flatMap(_.toJWebSocketScheme).get == jWebSocketScheme,
        )
      }
    },
  )

  private def jHttpScheme: Gen[Any, HttpScheme] = Gen.fromIterable(List(HttpScheme.HTTP, HttpScheme.HTTPS))

  private def jWebSocketScheme: Gen[Any, WebSocketScheme] =
    Gen.fromIterable(List(WebSocketScheme.WS, WebSocketScheme.WSS))
}
