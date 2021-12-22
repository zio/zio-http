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
      val actual = Http.ok.requestBodyAsString()
      assertM(actual)(anything)
    } +
      testM("non empty content") {
        val actual          = Http.response(Response(status = Status.OK, data = HttpData.fromText("abc")))
        val responseContent = actual.requestBody()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("POST request expect non empty response content") {
        val headers  = Headers.userAgent("zio-http test")
        val response = Http.response(Response(status = Status.OK, data = HttpData.fromText("abc"), headers = headers))
        val responseContent = response.requestBody()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("empty content") {
        val actual          = Http.empty
        val responseContent = actual.requestBody()
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val actual = Http.response(Response(status = Status.OK, data = HttpData.fromText("zio user does not exist")))
        val responseContent = actual.requestBodyAsString()
        assertM(responseContent)(containsString("user"))
      }

  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live
}
