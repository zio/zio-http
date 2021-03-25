package zhttp.service

import zio.test.assertM
import zio.test.Assertion.anything

object ClientSpec extends HttpRunnableSpec {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("https://api.github.com/users/zio/repos")
      assertM(actual)(anything)
    },
  ).provideCustomLayer(env)
}
