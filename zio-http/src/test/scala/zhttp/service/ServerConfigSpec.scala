package zhttp.service

import io.netty.handler.codec.http.HttpVersion
import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object ServerConfigSpecExperimental extends HttpRunnableSpec(8088) {

  /*
    From github discussion (Keep Alive Test case 2, Scenario 1)

      https://github.com/dream11/zio-http/discussions/592
   */
  def keepAliveSpec = suite("KeepAlive Test cases") {
    suite("Connection: close request header test with Server KeepAlive ENABLED") {
      val app1 = HttpApp.empty
      testM(
        "Http 1.1 WITHOUT 'Connection: close' in the request header SHOULD respond WITHOUT 'Connection: close' in the response header, indicating re-use",
      ) {
        val res = app1.request().map(_.getHeaderValue("Connection"))
        assertM(res)(isNone)
      } +
        testM(
          "Http 1.1 WITH 'Connection: close' in the request header SHOULD respond WITH 'Connection: close' in the response header, indicating NO re-use",
        ) {
          val path    = !!
          val headers = List(Header.connectionClose)
          val res     = app1.request(path, Method.GET, "", headers).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
        testM("For Http 1.0 any request SHOULD respond with 'connection: close' response header indicating no re-use") {
          val path    = !!
          val headers = List()
          val res     =
            app1.request(path, Method.GET, "", headers, HttpVersion.HTTP_1_0).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    } // +
  }

  override def spec = {
    suiteM("ServerConfig KeepAlive Enabled Server") {
      appKeepAliveEnabled.as(List(keepAliveSpec)).useNow
    }.provideCustomLayerShared(env)
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live

  val keepAliveServerConf         = Server.keepAlive
  private val appKeepAliveEnabled = configurableServe(AppCollection.app, keepAliveServerConf)
}
