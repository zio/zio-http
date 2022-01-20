package zhttp.http

import io.netty.buffer.{ByteBuf, Unpooled}
import zhttp.internal.HttpGen
import zhttp.service.server.ContentDecoder
import zio.test.Assertion.{anything, equalTo, fails, isSubtype}
import zio.test._

object ContentDecoderSpec extends DefaultRunnableSpec {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder") {
    testM("text") {
      checkAllM(content, HttpGen.method, HttpGen.url, Gen.listOf(HttpGen.header)) { (c, m, u, h) =>
        assertM(ContentDecoder.text.decode(c, m, u, Headers(h)))(equalTo("ABCD"))
      }
    } +
      testM("collectAll") {
        checkAllM(content, HttpGen.method, HttpGen.url, Gen.listOf(HttpGen.header)) { (c, m, u, h) =>
          val sampleStepDecoder = ContentDecoder.collectAll[ByteBuf]
          assertM(
            sampleStepDecoder
              .decode(c, m, u, Headers(h))
              .map(ch => ch.fold(Unpooled.EMPTY_BUFFER)((a, b) => b.writeBytes(a)).toString(HTTP_CHARSET)),
          )(
            equalTo("ABCD"),
          )
        }
      } +
      testM("backpressure") {
        checkAllM(content, HttpGen.method, HttpGen.url, Gen.listOf(HttpGen.header)) { (c, m, u, h) =>
          val sampleStepDecoder = ContentDecoder.backPressure
          assertM(
            sampleStepDecoder
              .decode(c, m, u, Headers(h))
              .flatMap(_.take)
              .map(_.toString(HTTP_CHARSET)),
          )(
            equalTo("ABCD"),
          )
        }
      } +
      testM("empty content") {
        checkAllM(HttpGen.method, HttpGen.url, Gen.listOf(HttpGen.header)) { (m, u, h) =>
          val sampleStepDecoder = ContentDecoder.backPressure
          assertM(
            sampleStepDecoder
              .decode(HttpData.empty, m, u, Headers(h))
              .flatMap(_.take)
              .map(_.toString(HTTP_CHARSET))
              .run,
          )(fails(isSubtype[ContentDecoder.Error](anything)))
        }
      }
  }
}
