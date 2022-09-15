package zhttp.service

import io.netty.handler.codec.http.HttpHeaderValues
import zhttp.http.{HeaderNames, Headers, Http, Version}
import zhttp.internal.DynamicServer
import zhttp.service.server._
import zio.http.internal.HttpRunnableSpec
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.assertM

object KeepAliveSpec extends HttpRunnableSpec {

  val app                   = Http.ok
  val connectionCloseHeader = Headers.connection(HttpHeaderValues.CLOSE)
  val keepAliveHeader       = Headers.connection(HttpHeaderValues.KEEP_ALIVE)
  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live
  private val appKeepAliveEnabled = serve(DynamicServer.app)

  def keepAliveSpec = suite("KeepAlive") {
    suite("Http 1.1") {
      testM("without connection close") {
        val res = app.deploy.headerValue(HeaderNames.connection).run()
        assertM(res)(isNone)
      } +
        testM("with connection close") {
          val res = app.deploy.headerValue(HeaderNames.connection).run(headers = connectionCloseHeader)
          assertM(res)(isSome(equalTo("close")))
        }
    } +
      suite("Http 1.0") {
        testM("without keep-alive") {
          val res = app.deploy.headerValue(HeaderNames.connection).run(version = Version.Http_1_0)
          assertM(res)(isSome(equalTo("close")))
        } +
          testM("with keep-alive") {
            val res = app.deploy
              .headerValue(HeaderNames.connection)
              .run(version = Version.Http_1_0, headers = keepAliveHeader)
            assertM(res)(isNone)
          }
      }
  }

  override def spec = {
    suiteM("ServerConfigSpec") {
      appKeepAliveEnabled.as(List(keepAliveSpec)).useNow
    }.provideCustomLayerShared(env)
  }

}
