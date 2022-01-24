package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def clientSpec = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.requestStatus()
      assertM(app)(equalTo(Status.OK))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.requestBody()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.getBodyAsString.map(Response.text(_)) }
        val res = app.requestBodyAsString(method = Method.POST, content = "ZIO user")
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
      } +
      testM("client req url") {
        val app    = Http.collect[Request] { case req @ Method.GET -> !! / "foo" =>
          Response.text(s"${req.url.asString}")
        }
        val actual = app.request(!! / "foo").flatMap(_.getBodyAsString)
        assertM(actual)(equalTo("/foo"))
      }
  }

  override def spec = {
    suiteM("Client") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
