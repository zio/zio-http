package zio.http.service

import io.netty.handler.codec.http.HttpHeaderValues
import zio._
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.{Headers, Method, Version}
import zio.http.netty.NettyRuntime
import zio.http.netty.client.ConnectionPool
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{diagnose, ignore, nonFlaky, sequential, timeout}
import zio.test._

object ConnectionPoolSpec extends HttpRunnableSpec {

  private val app = Http.collectZIO[Request] {
    case req @ Method.POST -> !! / "streaming" => ZIO.succeed(Response(body = Body.fromStream(req.body.asStream)))
    case req                                   => req.body.asString.map(Response.text(_))
  }

  private val connectionCloseHeader = Headers.connection(HttpHeaderValues.CLOSE)
  private val keepAliveHeader       = Headers.connection(HttpHeaderValues.KEEP_ALIVE)
  private val appKeepAliveEnabled   = serve(DynamicServer.app)

  private val N = 64

  def connectionPoolTests(
    version: Version,
    casesByHeaders: Map[String, Headers],
  ): Spec[Scope with Client with DynamicServer, Throwable] =
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
          } @@ nonFlaky(10),
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
          } @@ nonFlaky(10),
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
          } @@ nonFlaky(10),
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
          } @@ nonFlaky(10),
        )
      },
    )

  def connectionPoolSpec
    : Spec[Scope with ClientConfig with EventLoopGroup with ChannelFactory with NettyRuntime, Throwable] =
    suite("ConnectionPool")(
      suite("fixed")(
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
      ).provideSome[Scope with ClientConfig with EventLoopGroup with ChannelFactory with NettyRuntime](
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.live,
        ConnectionPool.fixed(2),
      ),
      suite("dynamic")(
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
      ).provideSome[Scope with ClientConfig with EventLoopGroup with ChannelFactory with NettyRuntime](
        ZLayer(appKeepAliveEnabled.unit),
        DynamicServer.live,
        severTestLayer,
        Client.live,
        ConnectionPool.dynamic(4, 16, 100.millis),
      ) @@ ignore, // TODO: there seems to be an issue in releasing dynamic ZPools
    )

  override def spec: Spec[Any, Throwable] = {
    connectionPoolSpec
      .provide(
        ClientConfig.default,
        Scope.default,
      ) @@ timeout(60.seconds) @@ sequential
  }

}
