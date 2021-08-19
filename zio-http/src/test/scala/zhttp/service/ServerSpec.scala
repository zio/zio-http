package zhttp.service

import zhttp.http._
import zhttp.service.server._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8081) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val app = serve {
    HttpApp.collectM {
      case Method.GET -> Root / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> Root / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> Root / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }
  }

  override def spec = suiteM("Server")(
    app
      .as(
        List(
          testM("200 response") {
            val actual = status(Root / "success")
            assertM(actual)(equalTo(Status.OK))
          },
          testM("500 response") {
            val actual = status(Root / "failure")
            assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
          },
          testM("404 response") {
            val actual = status(Root / "random")
            assertM(actual)(equalTo(Status.NOT_FOUND))
          },
          testM("200 response with encoded path") {
            val actual = status(Root / "get%2Fsuccess")
            assertM(actual)(equalTo(Status.OK))
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
