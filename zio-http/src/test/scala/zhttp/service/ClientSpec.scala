package zhttp.service

import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.test.Assertion.anything
import zio.test.{DefaultRunnableSpec, assertM}

object ClientSpec extends DefaultRunnableSpec {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("http://api.github.com/users/zio/repos", ClientSSLOptions.DefaultSSL)
      assertM(actual)(anything)
    },
  ).provideCustomLayer(env)
}
