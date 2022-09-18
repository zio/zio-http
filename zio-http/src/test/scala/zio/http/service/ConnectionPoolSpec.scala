package zio.http.service

import io.netty.handler.codec.http.HttpHeaderValues
import zio._
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.headers.Headers
import zio.http.model.{Method, Version}
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{diagnose, ignore, sequential, timeout}
import zio.test._

object ConnectionPoolSpec extends HttpRunnableSpec {

  private val app = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / "streaming" => ZIO.succeed(Response(body = Body.fromStream(req.body.asStream)))
    case req                                   => req.body.asString.map(Response.text(_))
  }

  private val connectionCloseHeader = Headers.connection(HttpHeaderValues.CLOSE)
  private val keepAliveHeader       = Headers.connection(HttpHeaderValues.KEEP_ALIVE)
  private val appKeepAliveEnabled   = serve(DynamicServer.app)

  private val N = 128

  def connectionPoolTests(
    version: Version,
    casesByHeaders: Map[String, Headers],
  ): Spec[Client with DynamicServer with Scope, Throwable] =
    suite(version.toString)(
      casesByHeaders.map { case (name, extraHeaders) =>
        suite(name)(
          test("not streaming") {
            val res =
              ZIO.foreachPar((1 to N).toList) { idx =>
                app.deploy.body
                  .run(
                    method = Method.POST,
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
              }
            assertZIO(res)(
              equalTo(
                (1 to N).map(_.toString).toList,
              ),
            )
          },
          test("streaming request") {
            val res      = ZIO.foreachPar((1 to N).toList) { idx =>
              val stream = ZStream.fromIterable(List("a", "b", "c-", idx.toString), chunkSize = 1)
              app.deploy.body
                .run(
                  method = Method.POST,
                  body = Body.fromStream(stream),
                  headers = extraHeaders,
                )
                .flatMap(_.asString)
            }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          },
          test("streaming response") {
            val res =
              ZIO.foreachPar((1 to N).toList) { idx =>
                app.deploy.body
                  .run(
                    method = Method.POST,
                    path = !! / "streaming",
                    body = Body.fromString(idx.toString),
                    headers = extraHeaders,
                  )
                  .flatMap(_.asString)
              }
            assertZIO(res)(
              equalTo(
                (1 to N).map(_.toString).toList,
              ),
            )
          },
          test("streaming request and response") {
            val res      = ZIO.foreachPar((1 to N).toList) { idx =>
              val stream = ZStream.fromIterable(List("a", "b", "c-", idx.toString), chunkSize = 1)
              app.deploy.body
                .run(
                  method = Method.POST,
                  path = !! / "streaming",
                  body = Body.fromStream(stream),
                  headers = extraHeaders,
                )
                .flatMap(_.asString)
            }
            val expected = (1 to N).map(idx => s"abc-$idx").toList
            assertZIO(res)(equalTo(expected))
          },
        )
      },
    )

  def connectionPoolSpec: Spec[Client with DynamicServer with Scope, Throwable] =
    suite("ConnectionPool")(
      connectionPoolTests(
        Version.Http_1_1,
        Map(
          "without connection close" -> Headers.empty,
          "with connection close"    -> connectionCloseHeader,
        ),
      ),
      connectionPoolTests(
        Version.Http_1_0,
        Map(
          "without keep-alive" -> Headers.empty,
          "with keep-alive"    -> keepAliveHeader,
        ),
      ),
    ) @@ ignore

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
    ) @@ timeout(5.seconds) @@ diagnose(5.seconds) @@ sequential
  }

}
