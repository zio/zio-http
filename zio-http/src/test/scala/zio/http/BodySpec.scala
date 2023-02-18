package zio.http

import java.io.File

import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.{ignore, timeout}
import zio.test._
import zio.{Chunk, durationInt}

import zio.stream.ZStream

import zio.http.model._

import io.netty.channel.embedded.EmbeddedChannel

object BodySpec extends ZIOSpecDefault {
  private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

  override def spec =
    suite("BodySpec")(
      suite("outgoing")(
        suite("encode")(
          suite("fromStream")(
            test("success") {
              check(Gen.string) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer, chunkSize = 2)
                val res             = Body.fromStream(responseContent).asString(HTTP_CHARSET)
                assertZIO(res)(equalTo(payload))
              }
            },
          ),
          suite("fromFile")(
            test("success") {
              lazy val file = testFile
              val res       = Body.fromFile(file).asString(HTTP_CHARSET)
              assertZIO(res)(equalTo("foo\nbar"))
            },
            test("success small chunk") {
              lazy val file = testFile
              val res       = Body.fromFile(file, 3).asString(HTTP_CHARSET)
              assertZIO(res)(equalTo("foo\nbar"))
            },
          ),
          suite("fromAsync")(
            test("success") {
              val ctx     = new EmbeddedChannel()
              val message = Chunk.fromArray("Hello World".getBytes(HTTP_CHARSET))
              val chunk   = Body.fromAsync(async => async(ctx, message, true)).asChunk
              assertZIO(chunk)(equalTo(message))
            },
            test("fail") {
              val exception = new RuntimeException("Some Error")
              val error     = Body.fromAsync(_ => throw exception).asChunk.flip
              assertZIO(error)(equalTo(exception))
            },
          ),
        ),
      ),
    ) @@ timeout(10 seconds)
}
