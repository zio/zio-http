package zhttp.http

import io.netty.handler.codec.http.HttpContent
import zhttp.experiment.HttpMessage
import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.ZIO
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
          val sampleStepDecoder = ContentDecoder.collectAll[HttpMessage[HttpContent]]
          assertM(sampleStepDecoder.decode(c).map(_.map(_.data).flatten).map(ch => new String(ch.toArray)))(
            equalTo("ABCD"),
          )
        }
      } +
      testM("Multipart") {
        val content = HttpGen.nonEmptyHttpData(Gen.const(List("a", "ab", "abc", "abcd", "abcde", "abcdef")))
        checkAllM(content) { c =>
          {
            val decoder                                 = ContentDecoder.multipart(ContentDecoder.testDecoder)
            val content: ZIO[Any, Throwable, List[Int]] =
              decoder.decode(c).flatMap(b => b.takeAll)
            assertM(content)(
              equalTo(List(21)),
            )
          }
        }
      }
  }
}
