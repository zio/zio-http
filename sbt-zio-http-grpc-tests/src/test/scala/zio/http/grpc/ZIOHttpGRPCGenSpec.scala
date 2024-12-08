package zio.http.grpc

import zio.test._
import zio.http.grpc.api._
import zio.http._

object ZIOHttpGRPCGenSpec extends ZIOSpecDefault {

  override def spec =
    suite("ZIOHttpGRPCGenSpec")(
      test("plugin generates Message") {
        val msg = TestMsg("msg")
        assertTrue(true)
      },
      test("plugin generates Endpoint") {
        val impl = V1.test.implementPurely { msg => msg }
        assertTrue(true)
      },
    )

}
