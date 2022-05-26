package zhttp.http

import zhttp.http.HttpData.ByteBufConfig
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion.{anything, equalTo, isLeft, isSubtype}
import zio.test.TestAspect.timeout
import zio.test._

import java.io.File

object HttpDataSpec extends DefaultRunnableSpec {

  override def spec =
    suite("HttpDataSpec") {

      val testFile = new File(getClass.getResource("/TestFile.txt").getPath)
      suite("outgoing") {
        suite("encode")(
          suite("fromStream") {
            testM("success") {
              checkM(Gen.anyString) { payload =>
                val stringBuffer    = payload.getBytes(HTTP_CHARSET)
                val responseContent = ZStream.fromIterable(stringBuffer)
                val res             = HttpData.fromStream(responseContent).toByteBuf.map(_.toString(HTTP_CHARSET))
                assertM(res)(equalTo(payload))
              }
            }
          },
          suite("fromFile")(
            testM("failure") {
              val res = HttpData.fromFile(throw new Error("Failure")).toByteBuf.either
              assertM(res)(isLeft(isSubtype[Error](anything)))
            },
            testM("success") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
              assertM(res)(equalTo("abc\nfoo"))
            },
            testM("success small chunk") {
              lazy val file = testFile
              val res       = HttpData.fromFile(file).toByteBuf(ByteBufConfig(3)).map(_.toString(HTTP_CHARSET))
              assertM(res)(equalTo("abc\nfoo"))
            },
          ),
        )
      }
    } @@ timeout(10 seconds)
}
