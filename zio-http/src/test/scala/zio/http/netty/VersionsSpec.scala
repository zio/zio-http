package zio.http.netty

import io.netty.handler.codec.http.HttpVersion
import zio._
import zio.http.Version
import zio.test._

object VersionsSpec extends ZIOSpecDefault {

  def spec =
    suite("Versions")(
      test("Should correctly convert from Netty to zio.http.") {
        implicit val unsafe = Unsafe.unsafe

        assertTrue(
          Versions.make(HttpVersion.HTTP_1_0) == Version.Http_1_0,
          Versions.make(HttpVersion.HTTP_1_1) == Version.Http_1_1,
        )
      },
      test("Should correctly convert from zio.http to Netty.") {

        assertTrue(
          Versions.make(Version.Http_1_0) == HttpVersion.HTTP_1_0,
          Versions.make(Version.Http_1_1) == HttpVersion.HTTP_1_1,
        )
      },
    )

}
