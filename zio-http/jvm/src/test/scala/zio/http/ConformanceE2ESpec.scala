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
  val configApp       = Server.Config.default
    .requestDecompression(true)
    .disableRequestStreaming(MaxSize)
    .port(port)
    .responseCompression()

  private val app = serve

  def conformanceSpec = suite("ConformanceE2ESpec")(
    test("should return 400 Bad Request if Host header is missing") {
      val routes = Handler.ok.toRoutes

      val res = routes.deploy.status.run(path = Path.root, headers = Headers(Header.Host("%%%%invalid%%%%")))
      assertZIO(res)(equalTo(Status.BadRequest))
    },
    test("should return 200 OK if Host header is present") {
      val routes = Handler.ok.toRoutes

      val res = routes.deploy.status.run(path = Path.root, headers = Headers(Header.Host("localhost")))
      assertZIO(res)(equalTo(Status.Ok))
    },
    test("should return 400 Bad Request if header contains CR, LF, or NULL (reject_fields_containing_cr_lf_nul)") {
      val routes = Handler.ok.toRoutes

      val resCRLF =
        routes.deploy.status.run(path = Path.root / "test", headers = Headers("InvalidHeader" -> "Value\r\n"))
      val resNull =
        routes.deploy.status.run(path = Path.root / "test", headers = Headers("InvalidHeader" -> "Value\u0000"))

      for {
        responseCRLF <- resCRLF
        responseNull <- resNull
      } yield assertTrue(
        responseCRLF == Status.BadRequest,
        responseNull == Status.BadRequest,
      )
    },
    test("should return 400 Bad Request if there is whitespace between start-line and first header field") {
      val route  = Method.GET / "test" -> Handler.ok
      val routes = Routes(route)

      val malformedRequest = Request
        .get("/test")
        .copy(headers = Headers.empty)
        .withBody(Body.fromString("\r\nHost: localhost"))

      val res = routes.deploy.status.run(path = Path.root / "test", headers = malformedRequest.headers)
      assertZIO(res)(equalTo(Status.BadRequest))
    },
    test("should return 400 Bad Request if there is whitespace between header field and colon") {
      val route  = Method.GET / "test" -> Handler.ok
      val routes = Routes(route)

      val requestWithWhitespaceHeader = Request.get("/test").addHeader(Header.Custom("Invalid Header ", "value"))

      val res = routes.deploy.status.run(path = Path.root / "test", headers = requestWithWhitespaceHeader.headers)
      assertZIO(res)(equalTo(Status.BadRequest))
    },
  )

  override def spec =
    suite("ConformanceE2ESpec") {
      val spec = conformanceSpec
      suite("app without request streaming") { app.as(List(spec)) }
    }.provideShared(
      DynamicServer.live,
      ZLayer.succeed(configApp),
      Server.customized,
      Client.default,
      ZLayer.succeed(NettyConfig.default),
    ) @@ sequential @@ withLiveClock

}
