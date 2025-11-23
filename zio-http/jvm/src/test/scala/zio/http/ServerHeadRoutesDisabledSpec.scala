package zio.http

import zio._
import zio.test.TestAspect._
import zio.test._

import zio.http.netty.NettyConfig

/**
 * Tests for behaviour when automatic HEAD route generation is disabled via
 * Server.Config.generateHeadRoutes = false.
 */
object ServerHeadRoutesDisabledSpec extends ZIOSpecDefault {
  private val disabledConfigLayer: ZLayer[Any, Nothing, Server.Config] =
    ZLayer.succeed(Server.Config.default.copy(generateHeadRoutes = false).onAnyOpenPort)

  override def spec =
    suite("ServerHeadRoutesDisabledSpec")(
      test("HEAD does not fall back to GET when disabled") {
        val routes = Routes(
          Method.GET / "users" -> Handler.text("User list"),
        )
        for {
          port     <- Server.installRoutes(routes)
          headResp <- Client.batched(Request.head(URL.root.port(port) / "users"))
          getResp  <- Client.batched(Request.get(URL.root.port(port) / "users"))
        } yield assertTrue(headResp.status == Status.NotFound, getResp.status == Status.Ok)
      },
      test("Explicit HEAD route still works when disabled") {
        val routes = Routes(
          Method.GET / "users"  -> Handler.text("GET response"),
          Method.HEAD / "users" -> Handler.ok.addHeader(Header.Custom("X-Head-Handler", "true")),
        )
        for {
          port     <- Server.installRoutes(routes)
          headResp <- Client.batched(Request.head(URL.root.port(port) / "users"))
        } yield assertTrue(
          headResp.status == Status.Ok,
          headResp.headers.get("X-Head-Handler").isDefined,
        )
      },
      test("HEAD with path parameter does not fall back to GET when disabled") {
        val routes = Routes(
          Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) => Response.text(s"User $id") },
        )
        for {
          port     <- Server.installRoutes(routes)
          headResp <- Client.batched(Request.head(URL.root.port(port) / "users" / "123"))
        } yield assertTrue(headResp.status == Status.NotFound)
      },
    ).provideShared(
      disabledConfigLayer,
      Server.customized,
      ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      Client.default,
    ) @@ sequential @@ withLiveClock
}
