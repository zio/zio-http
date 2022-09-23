package zio.http.netty

import io.netty.handler.codec.http.HttpVersion
import zio.http.model.Version
import zio.test._

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
