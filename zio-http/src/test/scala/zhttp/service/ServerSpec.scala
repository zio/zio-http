package zhttp.service

import zhttp.http._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8087) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

  val app = serve {
    HttpApp.collectM {
      case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> !! / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }
  }

  override def spec = suite("Server")(
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
            },
  ).provideCustomLayerShared(env ++ (app.toLayer.orDie))
}