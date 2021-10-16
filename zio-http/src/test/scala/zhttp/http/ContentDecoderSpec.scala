package zhttp.http

import zhttp.experiment.internal.{HttpGen, HttpMessageAssertions}
import zio.test.Assertion.equalTo
import zio.test._
import zio.{Chunk, ZIO}

object ContentDecoderSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  val content = HttpGen.nonEmptyHttpData(Gen.const(List("A", "B", "C", "D")))

  def spec = suite("ContentDecoder.decoder()") {
    testM("Text decoder") {
      checkAllM(content) { case c =>
        assertM(ContentDecoder.text.decode(c))(equalTo("ABCD"))
      }
    } +
      testM("Step decoder") {
        checkAllM(content) { case c =>
          val sampleStepDecoder: ContentDecoder[Any, Nothing, Chunk[Byte], String] =
            ContentDecoder.collect(Chunk.fromIterable(List.empty[Byte]))((a, s, b) =>
              if (b == true) {
                val state = s ++ a
                ZIO.succeed((Some(new String(state.toArray, HTTP_CHARSET)), state))
              } else {
                val state = s ++ a
                ZIO.succeed((None, state))
              },
            )
          assertM(sampleStepDecoder.decode(c))(equalTo("ABCD"))
        }
      }
  }
}
