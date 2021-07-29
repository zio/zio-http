package zhttp.service

import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.Assertion.anything
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = HChannelFactory.auto ++ HEventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("http://api.github.com/users/zio/repos", ClientSSLOptions.DefaultSSL)
      assertM(actual)(anything)
    },
  ).provideCustomLayer(env)
}
