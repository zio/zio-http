package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme
import zhttp.internal.HttpGen
import zio.test._

object SchemeSpec extends DefaultRunnableSpec {
  override def spec = schemeSpec

  def schemeSpec = suite("SchemeSpec") {
    testM("string") {
      checkAll(HttpGen.scheme) { scheme =>
        assertTrue(Scheme.decode(scheme.encode).get == scheme)
      }
    } +
      testM("java http scheme") {
        checkAll(jHttpScheme) { jHttpScheme =>
          assertTrue(Scheme.fromJScheme(jHttpScheme).flatMap(_.toJHttpScheme).get == jHttpScheme)
        }
      } +
      testM("java websocket scheme") {
        checkAll(jWebSocketScheme) { jWebSocketScheme =>
          assertTrue(
            Scheme.fromJScheme(jWebSocketScheme).flatMap(_.toWebSocketScheme).get == jWebSocketScheme,
          )
        }
      }
  }

  def jHttpScheme: Gen[Any, HttpScheme] = Gen.fromIterable(List(HttpScheme.HTTP, HttpScheme.HTTPS))

  def jWebSocketScheme: Gen[Any, WebSocketScheme] = Gen.fromIterable(List(WebSocketScheme.WS, WebSocketScheme.WSS))
}
