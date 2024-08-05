package zio.http

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.http.internal.HttpRunnableSpec
import zio.http.netty.NettyConfig

object ErrorInBodySpec extends ZIOHttpSpec {

  def spec =
    suite("ErrorInBodySpec")(
      test("error not in body by default") {
        val routes = Routes(Method.GET / "test" -> Handler.ok.map(_ => throw new Throwable("Error")))
        assertZIO(for {
          port   <- Server.install(routes)
          client <- ZIO.service[Client]
          url = URL.decode("http://localhost:%d/%s".format(port, Path.root / "test")).toOption.get
          body    <- client(Request(url = url)).map(_.body)
          content <- body.asString
        } yield content)(isEmptyString) &&
        assertTrue(routes.routes.map(_.errorState) == Chunk(false))
      },
      test("include error in body") {
        val routes =
          Routes(Method.GET / "test2" -> Handler.ok.map(_ => throw new Throwable("Error"))).includeErrorDetails
        assertZIO(for {
          port   <- Server.install(routes)
          client <- ZIO.service[Client]
          url = URL.decode("http://localhost:%d/%s".format(port, Path.root / "test2")).toOption.get
          body    <- client(Request(url = url)).map(_.body)
          content <- body.asString
        } yield content)(not(isEmptyString)) &&
        assertTrue(routes.routes.map(_.errorState) == Chunk(true))
      },
      test("exclude error in body") {
        val routes = Routes(Method.GET / "test3" -> Handler.ok.map(_ => throw new Throwable("Error")))
        assertZIO(for {
          port   <- Server.install(routes.includeErrorDetails.excludeErrorDetails)
          client <- ZIO.service[Client]
          url = URL.decode("http://localhost:%d/%s".format(port, Path.root / "test3")).toOption.get
          body    <- client(Request(url = url)).map(_.body)
          content <- body.asString
        } yield content)(isEmptyString) &&
        assertTrue(routes.routes.map(_.errorState) == Chunk(false))
      },
    ).provideSome[Server & Client](Scope.default)
      .provideShared(
        ZLayer.succeed(Server.Config.default),
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        Client.default,
      ) @@ sequential @@ withLiveClock

}
