package zhttp.service

import zhttp.http._
//import zhttp.service.Server.make
import zhttp.service.server.Transport
//import zhttp.service.server._
import zio.ZIO
import zio.test.Assertion.equalTo
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8087) {

  private val serverApp: HttpApp[Any, Throwable] =
    HttpApp.collectM {
      case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
      case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
      case Method.GET -> !! / "get%2Fsuccess" =>
        ZIO.succeed(Response.ok)
    }

  private val server: Server[Any,Throwable] =
    Server.port(8087) ++              // Setup port
      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
      Server.app(serverApp)   ++  // Setup the Http app
      Server.serverChannel(Transport.Auto)

  val env = EventLoopGroup.auto() ++ ChannelFactory.auto // ++ ServerChannelFactory.auto

  override def spec =
    suite("Server")(

      testM("200 response") {
        server
          .make
          .use{_ =>
            val actual = status(!! / "success")
            assertM(actual)(equalTo(Status.OK))
          }
      } //+
//      testM("500 response") {
//        val actual = status(!! / "failure")
//        assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
//      } +
//      testM("404 response") {
//        val actual = status(!! / "random")
//        assertM(actual)(equalTo(Status.NOT_FOUND))
//      } +
//      testM("200 response with encoded path") {
//        val actual = status(!! / "get%2Fsuccess")
//        assertM(actual)(equalTo(Status.OK))
//      }

//        List(
//            testM("500 response") {
//              val actual = status(!! / "failure")
//              assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
//            } +
//            testM("404 response") {
//              val actual = status(!! / "random")
//              assertM(actual)(equalTo(Status.NOT_FOUND))
//            } +
//            testM("200 response with encoded path") {
//              val actual = status(!! / "get%2Fsuccess")
//              assertM(actual)(equalTo(Status.OK))
//            }
//        )


//    app
//      .as(
//        List(
//          testM("200 response") {
//            val actual = status(!! / "success")
//            assertM(actual)(equalTo(Status.OK))
//          } +
//            testM("500 response") {
//              val actual = status(!! / "failure")
//              assertM(actual)(equalTo(Status.INTERNAL_SERVER_ERROR))
//            } +
//            testM("404 response") {
//              val actual = status(!! / "random")
//              assertM(actual)(equalTo(Status.NOT_FOUND))
//            } +
//            testM("200 response with encoded path") {
//              val actual = status(!! / "get%2Fsuccess")
//              assertM(actual)(equalTo(Status.OK))
//            },
//        ),
//      )
//      .useNow,
  ).provideCustomLayer(env)
}
