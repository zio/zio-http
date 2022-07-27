package zhttp.http

import zhttp.http.HttpData.ByteBufConfig
import zio.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.timeout
import zio.test._

import java.io.File

object HttpDataSpec extends ZIOSpecDefault {
  private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

  override def spec =
    suite("HttpDataSpec")(
      suite("outgoing")(
        suite("encode")(
          suite("fromStream")(
            test("success") {
              check(Gen.string) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = HttpData.fromStream(responseContent).toByteBuf.map(_.toString(HTTP_CHARSET))
                assertZIO(res)(equalTo(payload))
              }
            },
          ),
          suite("fromFile")(
            test("failure") {
              val res = HttpData.fromFile(throw new Error("Failure")).toByteBuf.either
              assertZIO(res)(isLeft(isSubtype[Error](anything)))
            },
            test("success") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
            test("success small chunk") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf(ByteBufConfig(3)).map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
          ),
        ),
      ),
    ) @@ timeout(10 seconds)
}
