package zio.http.service

import io.netty.handler.codec.http.HttpHeaderValues
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertZIO
import zio.{Scope, durationInt}

object KeepAliveSpec extends HttpRunnableSpec {

  val app                         = Http.ok
  val connectionCloseHeader       = Headers.connection(HttpHeaderValues.CLOSE)
  val keepAliveHeader             = Headers.connection(HttpHeaderValues.KEEP_ALIVE)
  private val appKeepAliveEnabled = serve(DynamicServer.app)

  def keepAliveSpec = suite("KeepAlive")(
    suite("Http 1.1")(
      test("without connection close") {
        val res = app.deploy.headerValue(HeaderNames.connection).run()
        assertZIO(res)(isNone)
      },
      test("with connection close") {
        val res = app.deploy.headerValue(HeaderNames.connection).run(headers = connectionCloseHeader)
        assertZIO(res)(isSome(equalTo("close")))
      },
    ),
    suite("Http 1.0")(
      test("without keep-alive") {
        val res = app.deploy.headerValue(HeaderNames.connection).run(version = Version.Http_1_0)
        assertZIO(res)(isSome(equalTo("close")))
      },
      test("with keep-alive") {
        val res = app.deploy
          .headerValue(HeaderNames.connection)
          .run(version = Version.Http_1_0, headers = keepAliveHeader)
        assertZIO(res)(isNone)
      },
    ),
  )

  override def spec = {
    suite("ServerConfigSpec") {
      appKeepAliveEnabled.as(List(keepAliveSpec))
    }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@ timeout(
      30.seconds,
    )
  }

}
