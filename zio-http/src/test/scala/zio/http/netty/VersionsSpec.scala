package zio.http.netty

import zio.test._

import zio.http.model.Version

import io.netty.handler.codec.http.HttpVersion

object VersionsSpec extends ZIOSpecDefault {

  def spec =
    suite("Versions")(
      test("Should correctly convert from zio.http to Netty.") {

        assertTrue(
          Versions.convertToZIOToNetty(Version.Http_1_0) == HttpVersion.HTTP_1_0,
          Versions.convertToZIOToNetty(Version.Http_1_1) == HttpVersion.HTTP_1_1,
        )
      },
    )

}
