package zhttp.service

import zhttp.http._
import zio.test.TestAspect.sequential
//import zhttp.service.server._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8087) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto //++ ServerChannelFactory.auto

  val app = serve {
    HttpApp.collectM {
      case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> !! / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }
  }

  override def spec = suite("Server")(
//    app
//      .as(
//        List(
          testM("200 response") {
            app
              .use { _ =>

              val actual = status(!! / "success")
            assertM(actual)(equalTo(Status.OK))
              }
          } ,
            testM("500 response") {
              app
                .use { _ =>

                val actual = status(!! / "failure")
              assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
                }
            } ,
            testM("404 response") {
              app
                .use { _ =>

                  val actual = status(!! / "random")
                  assertM(actual)(equalTo(Status.NOT_FOUND))
                }
            } ,
            testM("200 response with encoded path") {
              app
                .use { _ =>

                  val actual = status(!! / "get%2Fsuccess")
                  assertM(actual)(equalTo(Status.OK))
                }
            },
//        ),
//      )
//      .useNow,
  ).provideCustomLayer(env) @@ sequential
}