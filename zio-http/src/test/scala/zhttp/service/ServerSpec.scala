package zhttp.service

import zhttp.http._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8087) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto // ++ ServerChannelFactory.auto

  val app = serveWithPort {
    HttpApp.collectM {
      case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> !! / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }
  } _

  override def spec = suite("Server")(
    testM("200 response") {
      val p = 18087
      app(p).use { _ =>
        val actual = statusWithPort(p, !! / "success")
        assertM(actual)(equalTo(Status.OK))
      }
    },
    testM("500 response") {
      val p = 18088
      app(p).use { _ =>
        val actual = statusWithPort(p,!! / "failure")
        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
      }
    },
    testM("404 response") {
      val p = 18089
      app(p).use { _ =>
        val actual = statusWithPort(p,!! / "random")
        assertM(actual)(equalTo(Status.NOT_FOUND))
      }
    },
    testM("200 response with encoded path") {
      val p = 18090
      app(p).use { _ =>
        val actual = statusWithPort(p,!! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.OK))
      }
    },
  ).provideCustomLayer(env)
}
