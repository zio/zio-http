package zhttp.service

import zio.test.Assertion.anything
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("https://api.github.com/users/zio/repos")
      assertM(actual)(anything)
    },
  ).provideCustomLayer(env)
}
