package otherorg

import zio.http.netty.server.NettyDriver
import zio.test._

import Assertion._

object NettyDriverVisibilitySpec extends ZIOSpecDefault {
  def spec = suite("NettyDriverVisibilitySpec")(
    test("NettyDriver is visible outside of zio") {
      val result = typeCheck {
        """
        val default = NettyDriver.default
        val make    = NettyDriver.make
        """
      }
      assertZIO(result)(isRight(anything))
    },
  )
}
