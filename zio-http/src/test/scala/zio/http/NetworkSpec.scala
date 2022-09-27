package zio.http

import zio.test.{ZIOSpecDefault, assertCompletes}

object NetworkSpec extends ZIOSpecDefault {
  def spec =
    test("find a port") {
      for {
        port1 <- NetworkLive.findOpenPort.debug
        port2 <- NetworkLive.findOpenPort.debug
        port3 <- NetworkLive.findOpenPort.debug
      } yield assertCompletes
    }

}
