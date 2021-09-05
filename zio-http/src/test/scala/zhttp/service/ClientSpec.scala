package zhttp.service

import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.Assertion.anything
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("http://api.github.com/users/zio/repos", ClientSSLOptions.DefaultSSL, false)
      assertM(actual)(anything)
    },
  ).provideCustomLayer(env)
}
