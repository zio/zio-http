package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, NewHttpRunnableSpec}
import zhttp.service.server._
//import zio.test.TestAspect.sequential
//import zio.duration.durationInt
import zio.test.Assertion._
//import zio.test.TestAspect.{timeout}
import zio.test._

//import java.net.ConnectException

object NewClientSpec extends NewHttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def clientSpec = suite("NewClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertM(app)(equalTo(Status.OK))
    } +
      testM("non empty content") {
        Thread.sleep(3000)
        val app             = Http.text("abc")
        val responseContent = app.deploy.body.run()
        assertM(responseContent)(isNonEmpty)
      }  // +
//      testM("echo POST request content") {
//        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
//        val res = app.deploy.bodyAsString.run(method = Method.POST, content = "ZIO user")
//        assertM(res)(equalTo("ZIO user"))
//      }   +
//      testM("empty content") {
//        val app             = Http.empty
//        val responseContent = app.deploy.body.run()
//        assertM(responseContent)(isEmpty)
//      } +
//      testM("text content") {
//        val app             = Http.text("zio user does not exist")
//        val responseContent = app.deploy.bodyAsString.run()
//        assertM(responseContent)(containsString("user"))
//      } //+
//      testM("handle connection failure") {
//        val res = Client.request("http://localhost:1").either
//        assertM(res)(isLeft(isSubtype[ConnectException](anything)))
//      }
  }

  override def spec = {
    suiteM("NewClient") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env)  //@@ timeout(5 seconds) //@@ sequential
//    }.provideCustomLayerShared(env) @@ sequential //@@ timeout(5 seconds) //@@ sequential
  }
}
