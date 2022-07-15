package zhttp.http

import zhttp.http.Body.ByteBufConfig
import zio.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.timeout
import zio.test._

import java.io.File

object BodySpec extends ZIOSpecDefault {

  override def spec =
    suite("BodySpec") {
      val testFile = new File(getClass.getResource("/TestFile.txt").getPath)
      suite("outgoing") {
        suite("encode")(
          suite("fromStream") {
            test("success") {
              check(Gen.string) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = Body.fromStream(responseContent).asByteBuf.map(_.toString(HTTP_CHARSET))
                assertZIO(res)(equalTo(payload))
              }
            }
          },
          suite("fromFile")(
            test("failure") {
              val res = Body.fromFile(throw new Error("Failure")).asByteBuf.either
              assertZIO(res)(isLeft(isSubtype[Error](anything)))
            },
            test("success") {
              lazy val file = testFile
              val res       = Body.fromFile(file).asByteBuf.map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
            test("success small chunk") {
              lazy val file = testFile
              val res       = Body.fromFile(file).asByteBuf(ByteBufConfig(3)).map(_.toString(HTTP_CHARSET))
              assertZIO(res)(equalTo("abc\nfoo"))
            },
          ),
        )
      }
    } @@ timeout(10 seconds)
}
