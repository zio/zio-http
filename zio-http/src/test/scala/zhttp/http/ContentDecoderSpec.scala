package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.Chunk
import zio.test.Assertion.equalTo
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))
  val reqMeta = HttpGen.reqMeta

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(reqMeta, content) { (m, c) => assertM(ContentDecoder.text.decode(m, c))(equalTo("ABCD")) }
    } +
      testM("collectAll") {
        checkAllM(reqMeta, content) { (m, c) =>
          val sampleStepDecoder = ContentDecoder.collectAll[(Method, URL, List[Header], Chunk[Byte])]
          assertM(sampleStepDecoder.decode(m, c).map(_.flatMap(_._4)).map(ch => new String(ch.toArray)))(
            equalTo("ABCD"),
          )
        }
      }
  }
}
