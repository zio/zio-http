package zhttp.http

import zio.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.timeout
import zio.test._

import java.io.File

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
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = Body.fromStream(responseContent).asString(HTTP_CHARSET)
                assertZIO(res)(equalTo(payload))
              }
            },
          ),
          suite("fromFile")(
            test("failure") {
              val res = Body.fromFile(throw new Error("Failure")).asChunk.either
              assertZIO(res)(isLeft(isSubtype[Error](anything)))
            },
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
        ),
      ),
    ) @@ timeout(10 seconds)
}
