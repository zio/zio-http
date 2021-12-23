package zhttp.service

import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object ClientSpec extends HttpRunnableSpec(8082) {

  override def spec = {
    suiteM("Client") {
      serve(AppCollection.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
  def clientSpec    = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.requestBodyAsString()
      assertM(app)(anything)
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.requestBody()
        assertM(responseContent)(isNonEmpty)
      } +
      testM(
        "POST request expect non empty response content from a server.",
      ) {
        val res = Http
          .text("ZIO user")
          .setMethod(Method.POST)
          .requestBodyAsString()

        assertM(res)(equalTo("ZIO user"))
      } +
      testM("empty content") {
        val app             = Http.empty
        val responseContent = app.requestBody()
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.requestBodyAsString()
        assertM(responseContent)(containsString("user"))
      }

  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live
}
