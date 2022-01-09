package zhttp.service

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpVersion}
import zhttp.http.{!!, Headers, Http, Method}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object ServerConfigSpec extends HttpRunnableSpec {

  def keepAliveSpec = suite("KeepAlive Test cases") {
    suite("Connection: close request header test with Server KeepAlive ENABLED") {
      val connectionCloseHeader = Headers(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.CLOSE.toString)
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
          val headers = connectionCloseHeader
          val res     = app1.request(path, Method.GET, "", headers).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
        testM("Http 1.0 Request => Response WITH 'connection: close' => NO re-use") {
          val path    = !!
          val headers = Headers.empty
          val res     =
            app1.request(path, Method.GET, "", headers, HttpVersion.HTTP_1_0).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    }
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  // uncomment below for testing with keep alive enabled explicitly
  val keepAliveServerConf         = Server.keepAlive
  private val appKeepAliveEnabled = configurableServe(DynamicServer.app, keepAliveServerConf)

  // uncomment below for testing without enabling keep alive explicitly.
  // private val appKeepAliveEnabled = serve(DynamicServer.app)

  override def spec = {
    suiteM("ServerConfig KeepAlive Enabled Server") {
      appKeepAliveEnabled.as(List(keepAliveSpec)).useNow
    }.provideCustomLayerShared(env)
  }

}
