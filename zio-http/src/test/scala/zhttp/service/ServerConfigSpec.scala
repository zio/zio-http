package zhttp.service

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http.{Headers, Http}
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
        val res = app.requestHeaderValueByName()(HttpHeaderNames.CONNECTION)
        assertM(res)(isNone)
      } +
        testM("with connection close") {
          val res = app.requestHeaderValueByName(headers = connectionCloseHeader)(HttpHeaderNames.CONNECTION)
          assertM(res)(isSome(equalTo("close")))
        }
    } +
      suite("Http 1.0") {
        testM("without keep-alive") {
          val res = app.requestHeaderValueByName(httpVersion = http10V)(HttpHeaderNames.CONNECTION)
          assertM(res)(isSome(equalTo("close")))
        } +
          testM("with keep-alive") {
            val res =
              app.requestHeaderValueByName(httpVersion = http10V, headers = keepAliveHeader)(HttpHeaderNames.CONNECTION)
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
