package zhttp.service

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpVersion}
import zhttp.http.{!!, Header, Http, Method}
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object ServerConfigSpec extends HttpRunnableSpec(8088) {

  def keepAliveSpec = suite("KeepAlive Test cases") {
    suite("Connection: close request header test with Server KeepAlive ENABLED") {
      val connectionCloseHeader = Header(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.CLOSE.toString)
      val app1                  = Http.empty
      testM(
        "Http 1.1  WITHOUT 'Connection: close' Request => Response WITHOUT 'Connection: close' => re-use",
      ) {
        val res = app1.request().map(_.getHeaderValue("Connection"))
        assertM(res)(isNone)
      } +
        testM(
          "Http 1.1 WITH 'Connection: close' Request => Response WITH 'Connection: close' => NO re-use",
        ) {
          val path    = !!
          val headers = List(connectionCloseHeader)
          val res     = app1.request(path, Method.GET, "", headers).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
        testM("Http 1.0 Request => Response WITH 'connection: close' => NO re-use") {
          val path    = !!
          val headers = List()
          val res     =
            app1.request(path, Method.GET, "", headers, HttpVersion.HTTP_1_0).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    }
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live

  val keepAliveServerConf         = Server.keepAlive
  private val appKeepAliveEnabled = configurableServe(AppCollection.app, keepAliveServerConf)

  override def spec = {
    suiteM("ServerConfig KeepAlive Enabled Server") {
      appKeepAliveEnabled.as(List(keepAliveSpec)).useNow
    }.provideCustomLayerShared(env)
  }

}
