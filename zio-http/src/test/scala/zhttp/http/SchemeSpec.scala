package zhttp.http

import zhttp.internal.HttpGen
import zio.test._

object SchemeSpec extends DefaultRunnableSpec {
  def schemeSpec = suite("SchemeSpec") {
    testM("string") {
      checkAll(HttpGen.scheme) { scheme =>
        assertTrue(Scheme.decode(scheme.encode).get == scheme)
      }
    } +
      testM("java http scheme") {
        checkAll(HttpGen.jHttpScheme) { jHttpScheme =>
          assertTrue(Scheme.fromJScheme(jHttpScheme).flatMap(_.toJHttpScheme).get == jHttpScheme)
        }
      } +
      testM("java websocket scheme") {
        checkAll(HttpGen.jWebSocketScheme) { jWebSocketScheme =>
          assertTrue(
            Scheme.fromJScheme(jWebSocketScheme).flatMap(_.toWebSocketScheme).get == jWebSocketScheme,
          )
        }
      }
  }

  override def spec = schemeSpec
}
