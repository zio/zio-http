package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assertM}

import java.io.File

object HttpDataSpec extends DefaultRunnableSpec {
  // TODO : Add tests for othe HttpData types
  override def spec =
    suite("HttpDataSpec")(suite("Test toByteBuf")(testM("HttpData.fromFile") {
      val file = new File(getClass.getResource("/TestFile.txt").getPath)
      val res  = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
      assertM(res)(equalTo("abc\nfoo"))
    }))
}
