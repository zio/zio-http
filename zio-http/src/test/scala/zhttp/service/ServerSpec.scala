package zhttp.service

import zhttp.http._
import zhttp.internal.AppCollection
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8088) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto ++ AppCollection.live

  val staticApp = HttpApp.collectM {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }

  val app = serve { staticApp +++ AppCollection.app }

  def staticAppSpec = suite("StaticAppSpec") {
    testM("200 response") {
      val actual = status(!! / "success")
      assertM(actual)(equalTo(Status.OK))
    } +
      testM("500 response") {
        val actual = status(!! / "failure")
        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
      } +
      testM("404 response") {
        val actual = status(!! / "random")
        assertM(actual)(equalTo(Status.NOT_FOUND))
      } +
      testM("200 response with encoded path") {
        val actual = status(!! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.OK))
      }
  }

  def dynamicAppSpec = suite("DynamicAppSpec") {
    testM("status is 200") {
      val status = HttpApp.ok.requestStatus()
      assertM(status)(equalTo(Status.OK))
    } @@ nonFlaky +
      testM("status is 404") {
        val status = HttpApp.empty.requestStatus()
        assertM(status)(equalTo(Status.NOT_FOUND))
      }
  }

  override def spec = {
    suiteM("Server")(app.as(List(dynamicAppSpec, staticAppSpec)).useNow).provideCustomLayerShared(env) @@ timeout(
      10 seconds,
    )
  }
}
