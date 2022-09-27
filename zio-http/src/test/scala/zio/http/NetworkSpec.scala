package zio.http

import zio.test._

object NetworkSpec extends ZIOSpecDefault {
  def spec =
    test("find a port") {
      for {
        port1 <- Network.findOpenPort
        port2 <- Network.findOpenPort
        port3 <- Network.findOpenPort
      } yield assertTrue(port1 != port2) && assertTrue(port2 != port3)
    }.provide(Network.live)

}
