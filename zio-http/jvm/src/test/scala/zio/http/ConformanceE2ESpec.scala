package zio.http

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.http._
import zio.http.internal.{DynamicServer, RoutesRunnableSpec}
import zio.http.netty.NettyConfig

object ConformanceE2ESpec extends RoutesRunnableSpec {
  private val port    = 8080
  private val MaxSize = 1024 * 10
  val config          = Server.Config.default
    .requestDecompression(true)
    .disableRequestStreaming(MaxSize)
    .port(port)
    .responseCompression()
    .validateHeaders(true)

  private val app     = serve
  def conformanceSpec = suite("ConformanceE2ESpec")(
    test("should return 400 Bad Request if Host header is missing") {
      val routes = Handler.ok.toRoutes
      val res    = routes.deploy.status.run(path = Path.root, headers = Headers(Header.Host("%%%%invalid%%%%")))
      assertZIO(res)(equalTo(Status.BadRequest))
    },
    test("should return 200 OK if Host header is present") {
      val routes = Handler.ok.toRoutes
      val res    = routes.deploy.status.run(path = Path.root, headers = Headers(Header.Host("localhost")))
      assertZIO(res)(equalTo(Status.Ok))
    },
  )
  override def spec   =
    suite("ConformanceE2ESpec") {
      val spec = conformanceSpec
      suite("app without request streaming") { app.as(List(spec)) }
    }.provideShared(
      Scope.default,
      DynamicServer.live,
      ZLayer.succeed(config),
      Server.customized,
      Client.default,
      ZLayer.succeed(NettyConfig.default),
    ) @@ sequential @@ withLiveClock
}
