package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, timeout}
import zio.test._

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def clientSpec = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.deploy.getStatus.run()
      assertM(app)(equalTo(Status.OK))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.deploy.getBody.run()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.getBodyAsString.map(Response.text(_)) }
        val res = app.deploy.getBodyAsString.run(method = Method.POST, content = "ZIO user")
        assertM(res)(equalTo("ZIO user"))
      } +
      testM("empty content") {
        val app             = Http.empty
        val responseContent = app.deploy.getBody.run()
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.deploy.getBodyAsString.run()
        assertM(responseContent)(containsString("user"))
      } +
      testM("handle connection failure") {
        val res = Client.request("http://localhost:1").either
        assertM(res)(isLeft(isSubtype[ConnectException](anything)))
      }
  }

  override def spec = {
    suiteM("Client") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
