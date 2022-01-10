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
    suite("Connection: close header") {
      testM("Http 1.1 WITHOUT 'Connection: close'") {
        val res = app.request().map(_.getHeaderValue("Connection"))
        assertM(res)(isNone)
      } +
        testM("Http 1.1 WITH 'Connection: close'") {
          val res = app.request(!!, Method.GET, "", connectionCloseHeader).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        }
    } +
      suite("Connection: keep-alive header") {
        testM("Http 1.0 WITHOUT 'connection: keep-alive'") {
          val res =
            app.request(!!, Method.GET, "", Headers.empty, HttpVersion.HTTP_1_0).map(_.getHeaderValue("Connection"))
          assertM(res)(isSome(equalTo("close")))
        } +
          testM("Http 1.0 WITH 'connection: keep-alive'") {
            val res = app
              .request(!!, Method.GET, "", keepAliveHeader, HttpVersion.HTTP_1_0)
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
