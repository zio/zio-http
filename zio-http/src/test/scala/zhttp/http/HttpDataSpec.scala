package zhttp.http

import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, Gen, assertM, checkAll}

import java.io.File

object HttpDataSpec extends DefaultRunnableSpec {
  // TODO : Add tests for othe HttpData types
  override def spec =
    suite("HttpDataSpec")(
      suite("Test toByteBuf")(
        test("HttpData.fromFile") {
          val file = new File(getClass.getResource("/TestFile.txt").getPath)
          val res  = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
          assertM(res)(equalTo("abc\nfoo"))
        },
        test("HttpData.fromStream") {
          checkAll(Gen.string) { payload =>
            val stringBuffer    = payload.toString.getBytes(HTTP_CHARSET)
            val responseContent = ZStream.fromIterable(stringBuffer)
            val res             = HttpData.fromStream(responseContent).toByteBuf.map(_.toString(HTTP_CHARSET))
            assertM(res)(equalTo(payload))
          }
        },
      ),
    )
}
