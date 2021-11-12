package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.Chunk
import zio.test.Assertion.equalTo
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(content, HttpGen.method, HttpGen.urlGen, Gen.listOf(HttpGen.header)) { (c, m, u, h) =>
        assertM(ContentDecoder.text.decode(c, m, u, h))(equalTo("ABCD"))
      }
    } +
      testM("collectAll") {
        checkAllM(content, HttpGen.method, HttpGen.urlGen, Gen.listOf(HttpGen.header)) { (c, m, u, h) =>
          val sampleStepDecoder = ContentDecoder.collectAll[Chunk[Byte]]
          assertM(sampleStepDecoder.decode(c, m, u, h).map(_.flatten).map(ch => new String(ch.toArray)))(
            equalTo("ABCD"),
          )
        }
      }
  }
}
