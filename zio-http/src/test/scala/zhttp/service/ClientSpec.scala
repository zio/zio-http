package zhttp.service

import zio.duration._
import zio.test.Assertion.anything
import zio.test.TestAspect.timeout
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {
  val env           = ChannelFactory.auto ++ EventLoopGroup.auto()
  override def spec = suite("Client")(
    testM("respond Ok") {
      val actual = Client.request("https://api.github.com/users/zio/repos")
      assertM(actual)(anything)
    } @@ timeout(20 second),
  ).provideCustomLayer(env)
}
