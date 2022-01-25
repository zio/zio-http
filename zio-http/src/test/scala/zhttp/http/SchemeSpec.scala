package zhttp.http

import zhttp.internal.HttpGen
import zio.test._

object SchemeSpec extends DefaultRunnableSpec {
  def schemeSpec = suite("SchemeSpec") {
    testM("string") {
      checkAll(HttpGen.scheme) { scheme =>
        assertTrue(Scheme.fromString(Scheme.asString(scheme)).get == scheme)
      }
    } +
      testM("java http scheme") {
        checkAll(HttpGen.jHttpScheme) { jHttpScheme =>
          assertTrue(Scheme.fromJHttpScheme(jHttpScheme).flatMap(Scheme.asJHttpScheme).get == jHttpScheme)
        }
      } +
      testM("java websocket scheme") {
        checkAll(HttpGen.jWebSocketScheme) { jWebSocketScheme =>
          assertTrue(
            Scheme.fromJWebSocketScheme(jWebSocketScheme).flatMap(Scheme.asJWebSocketScheme).get == jWebSocketScheme,
          )
        }
      }
  }

  override def spec = schemeSpec
}
