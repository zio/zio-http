package otherorg

import zio.test.Assertion._
import zio.test._

import zio.http.netty.server.NettyDriver

object NettyDriverVisibilitySpec extends ZIOSpecDefault {
  def spec = suite("NettyDriverVisibilitySpec")(
    test("NettyDriver is visible outside of zio") {
      val result = typeCheck {
        """
        val default = zio.http.netty.server.nettyDriverDefault
        val make    = zio.http.netty.server.nettyDriverMake
        val manual    = zio.http.netty.server.nettyDriverManual
        """
      }
      assertZIO(result)(isRight(anything))
    },
  )
}
