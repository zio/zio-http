package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicClient, DynamicServer, NewHttpRunnableSpec}
import zhttp.service.server._
//import zio.test.TestAspect.sequential
//import zio.duration.durationInt
import zio.test.Assertion._
//import zio.test.TestAspect.{timeout}
import zio.test._

//import java.net.ConnectException

object EnhancedClientSpec extends NewHttpRunnableSpec {

//  val oneClient = Client.make(ClientSettings.maxTotalConnections(20))

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ DynamicClient.live

  def clientSpec = suite("NewClientSpec") {
    val app1 = Http.collectHttp[Request] {
      case _ -> !! / "NonEmpty" => Http.text("abc")
      case _ -> !! / "Empty" => Http.empty
    }

    //    testM("respond Ok") {
//      val app = Http.ok.deploy.status.run()
//      assertM(app)(equalTo(Status.OK))
//    }  +
      testM("non empty content") {
//        val app             = Http.text("abc")
        val responseContent = app1.deploy.body.run(Path("/NonEmpty"))
        assertM(responseContent)(isNonEmpty)
      }   +
//      testM("echo POST request content") {
//        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
//        val res = app.deploy.bodyAsString.run(method = Method.POST, content = "ZIO user")
//        assertM(res)(equalTo("ZIO user"))
//      }   //+
      testM("empty content") {
//        val app             = Http.empty
        val responseContent = app1.deploy.body.run(Path("/Empty"))
        assertM(responseContent)(isEmpty)
      } //+
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
    }.provideCustomLayerShared(env)
//    }.provideCustomLayerShared(env) @@ sequential //@@ timeout(5 seconds)
  }
}
