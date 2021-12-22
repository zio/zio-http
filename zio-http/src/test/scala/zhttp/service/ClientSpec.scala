package zhttp.service

import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zio.Task
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object ClientSpec extends HttpRunnableSpec(8082) {

  private val ClientUserAgentValue = "ZIO HTTP Client"

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
        "POST request expect non empty response content from a server that requires a mandatory user agent header.",
      ) {
        val app = Http.text("test data") ++ Http.collectM[Request] { case req =>
          val userAgentHeader       = req.getHeaderValue(Headers.Literals.Name.UserAgent)
          val isExpectedHeaderValue = userAgentHeader.contains(ClientUserAgentValue)
          if (isExpectedHeaderValue) req.getBodyAsString.map(text => Response.text(text))
          else
            Task.succeed(Response.status(Status.FORBIDDEN))

        }

        val userAgentHeader = Headers.userAgent(ClientUserAgentValue)
        val response        = app.setMethod(Method.POST).addHeader(userAgentHeader)
        val responseContent = response.requestBody()
        assertM(responseContent)(isNonEmpty)
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
