package zhttp.http

import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, ZSpec, assertM}

import java.io.File

object ToByteBufSpec extends DefaultRunnableSpec {
  val suite1 = suite("ByteBufSpec")(testM("Bytebuf from file data") {
    val file = new File(getClass.getResource("/TestFile.txt").getPath)
    val res  = HttpData.fromFile(file).toByteBuf.map(_.toString(HTTP_CHARSET))
    assertM(res)(equalTo("abc\nfoo"))
  })
  override def spec: ZSpec[_root_.zio.test.environment.TestEnvironment, Any] = suite("ToByteBufSpec")(suite1)
}
