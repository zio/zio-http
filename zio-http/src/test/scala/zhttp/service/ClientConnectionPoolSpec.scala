package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, NewHttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion._
import zio.test._

object ClientConnectionPoolSpec extends NewHttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ClientFactory.client

  def connectionReuseSpec = suite("Connection Reuse Spec") {
    testM("reuse connection") {
      val app = Http.empty
      val res = for {
        _      <- app.deploy.run()
        count1 <- clientConnections
        _      <- app.deploy.run()
        count2 <- clientConnections
      } yield (count1 == count2)
      assertM(res)(isTrue)
    } +
      testM("large response") {
        assertCompletesM
      } +
      testM("parallel requests") {
        assertCompletesM
      } +
      testM("sequential requests") {
        assertCompletesM
      }
  } @@ zio.test.TestAspect.ignore

  override def spec = {
    suiteM("ClientConnectionPool") {
      serve(DynamicServer.app).as(List(connectionReuseSpec)).useNow
    }.provideCustomLayerShared(env)
  }
}
