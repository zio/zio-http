package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.Chunk
import zio.test.Assertion.equalTo
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(content) { c =>
        assertM(Request.toChunk(c).flatMap(v => ContentDecoder.text.decode(v)))(equalTo("ABCD"))
      }
    } +
      testM("collectAll") {
        checkAllM(content) { c =>
          val sampleStepDecoder = ContentDecoder.collectAll[Chunk[Byte]]
          assertM(
            Request.toChunk(c).flatMap(sampleStepDecoder.decode(_)).map(_.flatten).map(ch => new String(ch.toArray)),
          )(equalTo("ABCD"))
        }
      }
  }
}
