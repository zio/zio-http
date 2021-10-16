package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.Chunk
import zio.test.Assertion.equalTo
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(content) { c => assertM(ContentDecoder.text.decode(c))(equalTo("ABCD")) }
    } +
      testM("collectAll") {
        checkAllM(content) { c =>
          val sampleStepDecoder = ContentDecoder.collectAll[Chunk[Byte]]
          assertM(sampleStepDecoder.decode(c).map(_.flatten).map(ch => new String(ch.toArray)))(equalTo("ABCD"))
        }
      }
  }
}
