package zio.http

import java.io.File

import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Scope, durationInt}

import zio.stream.ZStream

import zio.http.model._

object BodySpec extends ZIOSpecDefault {
  private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

  override def spec: Spec[TestEnvironment with Scope, Throwable] =
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
        ),
      ),
    ) @@ timeout(10 seconds)
}
