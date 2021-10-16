package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.test.Assertion.equalTo
import zio.test._

object DecodeContentSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder.decoder()") {
    testM("Text ContentDecoder") {
      checkAllM(content) { case c =>
        assertM(ContentDecoder.text.decode(c))(equalTo("ABCD"))
      }
    }
  }
}