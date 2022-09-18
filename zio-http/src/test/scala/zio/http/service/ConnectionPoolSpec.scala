package zio.http.service

import io.netty.handler.codec.http.HttpHeaderValues
import zio._
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.headers.Headers
import zio.http.model.{Status, Version}
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{diagnose, sequential, timeout}
import zio.test._

object ConnectionPoolSpec extends HttpRunnableSpec {

  val app                         = Http.ok
  val connectionCloseHeader       = Headers.connection(HttpHeaderValues.CLOSE)
  val keepAliveHeader             = Headers.connection(HttpHeaderValues.KEEP_ALIVE)
  private val appKeepAliveEnabled = serve(DynamicServer.app)

  def connectionPoolSpec = suite("ConnectionPool")(
    suite("Http 1.1")(
      test("without connection close") {
        val res =
          ZIO.foreachPar((1 to 16).toList) { _ =>
            app.deploy.status
              .run()
          }
        assertZIO(res)(
          equalTo(
            List.fill(16)(Status.Ok),
          ),
        )
      },
      test("with connection close") {
        val res =
          ZIO.foreachPar((1 to 16).toList) { _ =>
            app.deploy.status
              .run(headers = connectionCloseHeader)
          }
        assertZIO(res)(
          equalTo(
            List.fill(16)(Status.Ok),
          ),
        )
      },
    ),
    suite("Http 1.0")(
      test("without keep-alive") {
        val res =
          ZIO.foreachPar((1 to 16).toList) { _ =>
            app.deploy.status
              .run(version = Version.Http_1_0)
          }
        assertZIO(res)(
          equalTo(
            List.fill(16)(Status.Ok),
          ),
        )
      },
      test("with keep-alive") {
        val res =
          ZIO.foreachPar((1 to 16).toList) { _ =>
            app.deploy.status
              .run(version = Version.Http_1_0, headers = keepAliveHeader)
          }
        assertZIO(res)(
          equalTo(
            List.fill(16)(Status.Ok),
          ),
        )
      },
    ),
  )

  override def spec = {
    suite("ConnectionPoolSpec") {
      appKeepAliveEnabled.as(List(connectionPoolSpec))
    }.provideShared(
      DynamicServer.live,
      severTestLayer,
      ClientConfig.default,
      Client.live,
//      ConnectionPool.disabled,
      ConnectionPool.fixed(2),
//      ConnectionPool.dynamic(4, 16, 500.millis),
      Scope.default,
    ) @@ timeout(30.seconds) @@ diagnose(30.seconds) @@ sequential
  }

}
