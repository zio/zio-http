package zhttp.service

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues, HttpVersion}
import zhttp.http.{!!, Headers, Http, Method}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object ServerConfigSpec extends HttpRunnableSpec {

  val app                   = Http.empty
  val connectionCloseHeader = Headers(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.CLOSE.toString)
  val keepAliveHeader       = Headers(HttpHeaderNames.CONNECTION.toString, HttpHeaderValues.KEEP_ALIVE.toString)

  def keepAliveSpec = suite("KeepAlive") {
    suite("Http 1.1") {
      testM("without connection close") {
        val res = app.request().map(_.getHeaderValue("Connection"))
        assertM(res)(isNone)
      } +
        testM("with connection close") {
          val res = app
            .request(defaultHttpVersion, !!, Method.GET, "", connectionCloseHeader)
            .map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    } +
      suite("Http 1.0") {
        testM("without keep-alive") {
          val res =
            app.request(HttpVersion.HTTP_1_0, !!, Method.GET, "", Headers.empty).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
          testM("with keep-alive") {
            val res = app
              .request(HttpVersion.HTTP_1_0, !!, Method.GET, "", keepAliveHeader)
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
