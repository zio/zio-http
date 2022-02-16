package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, NewHttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion._
import zio.test._

object EnhancedClientSpec extends NewHttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ClientFactory.client

  def clientSpec = suite("EnhancedClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertM(app)(equalTo(Status.OK))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.deploy.body.run()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
        val res = app.deploy.bodyAsString.run(method = Method.POST, content = "ZIO user")
        assertM(res)(equalTo("ZIO user"))
      } +
      testM("empty content") {
        val app             = Http.empty
        val responseContent = app.deploy.body.run()
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.deploy.bodyAsString.run()
        assertM(responseContent)(containsString("user"))
      }
  }

  override def spec = {
    suiteM("EnhancedClient") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env)
  }
}
