package zhttp.service

import zhttp.http._
import zhttp.internal.AppCollection
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.{nonFlaky, timeout}
import zio.test.assertM

object ServerSpec extends HttpRunnableSpec(8088) {
  val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live

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
    suite("success") {
      testM("status is 200") {
        val status = HttpApp.ok.requestStatus()
        assertM(status)(equalTo(Status.OK))
      } +
        testM("status is 200") {
          val res = HttpApp.text("ABC").requestStatus()
          assertM(res)(equalTo(Status.OK))
        } +
        testM("content is set") {
          val res = HttpApp.text("ABC").requestBodyAsString()
          assertM(res)(containsString("ABC"))
        }
    } +
      suite("not found") {
        val app = HttpApp.empty
        testM("status is 404") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.NOT_FOUND))
        } +
          testM("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(equalTo("0")))
          }
      } +
      suite("error") {
        val app = HttpApp.fail(new Error("SERVER_ERROR"))
        testM("status is 500") {
          val res = app.requestStatus()
          assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
        } +
          testM("content is set") {
            val res = app.requestBodyAsString()
            assertM(res)(containsString("SERVER_ERROR"))
          } +
          testM("header is set") {
            val res = app.request().map(_.getHeaderValue("Content-Length"))
            assertM(res)(isSome(equalTo("29")))
          }
      }
  }

  override def spec = {
    suiteM("Server") {
      app.as(List(staticAppSpec @@ nonFlaky, dynamicAppSpec @@ nonFlaky)).useNow
    }.provideCustomLayerShared(env) @@ timeout(10 seconds)
  }
}
