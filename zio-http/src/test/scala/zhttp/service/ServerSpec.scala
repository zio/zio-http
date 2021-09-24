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
      case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> !! / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }
  }

  override def spec = suiteM("Server")(
    app
      .as(
        List(
          test("200 response") {
            val actual = status(!! / "success")
            assertM(actual)(equalTo(Status.OK))
          },
          test("500 response") {
            val actual = status(!! / "failure")
            assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
          },
          test("404 response") {
            val actual = status(!! / "random")
            assertM(actual)(equalTo(Status.NOT_FOUND))
          },
          test("200 response with encoded path") {
            val actual = status(!! / "get%2Fsuccess")
            assertM(actual)(equalTo(Status.OK))
          },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
