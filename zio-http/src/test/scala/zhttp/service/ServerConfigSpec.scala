package zhttp.service

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpVersion}
import zhttp.http.{!!, Headers, Http, Method}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object ServerConfigSpec extends HttpRunnableSpec {

  def keepAliveSpec = suite("KeepAlive") {
    suite("Connection: close header") {
      val connectionCloseHeader = Headers(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.CLOSE.toString)
      val app1                  = Http.empty
      testM(
        "Http 1.1 WITHOUT 'Connection: close'",
      ) {
        val res = app1.request().map(_.getHeaderValue("Connection"))
        assertM(res)(isNone)
      } +
        testM(
          "Http 1.1 WITH 'Connection: close'",
        ) {
          val path = !!
          val res  = app1.request(path, Method.GET, "", connectionCloseHeader).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    } +
      suite("Connection: keep-alive header") {
        val keepAliveHeader = Headers(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.KEEP_ALIVE.toString)
        val app1            = Http.empty
        testM("Http 1.0 WITHOUT 'connection: keep-alive'") {
          val path = !!
          val res  =
            app1.request(path, Method.GET, "", Headers.empty, HttpVersion.HTTP_1_0).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
          testM("Http 1.0 WITH 'connection: keep-alive'") {
            val path = !!
            val res  =
              app1
                .request(path, Method.GET, "", keepAliveHeader, HttpVersion.HTTP_1_0)
                .map(_.getHeaderValue("Connection"))
            assertM(res)(isNone)
          }
      }
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live
  private val appKeepAliveEnabled = serve(DynamicServer.app)

  override def spec = {
    suiteM("ServerConfigSpec") {
      appKeepAliveEnabled.as(List(keepAliveSpec)).useNow
    }.provideCustomLayerShared(env)
  }

}
