package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Scope, ZIO, durationInt}

object StaticServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ Scope.default

  private val staticApp = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "die"           => ZIO.die(new RuntimeException("DIE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }

  // Use this route to test anything that doesn't require ZIO related computations.
  private val nonZIO = Http.collectHExit[Request] {
    case _ -> !! / "HExitSuccess" => HExit.succeed(Response.ok)
    case _ -> !! / "HExitFailure" => HExit.fail(new RuntimeException("FAILURE"))
    case _ -> !! / "throwable"    => throw new Exception("Throw inside Handler")
  }

  private val app = serve { nonZIO ++ staticApp }

  def nonZIOSpec = suite("NonZIOSpec") {
    val methodGenWithoutHEAD: Gen[Any, Method] = Gen.fromIterable(
      List(
        Method.OPTIONS,
        Method.GET,
        Method.POST,
        Method.PUT,
        Method.PATCH,
        Method.DELETE,
        Method.TRACE,
        Method.CONNECT,
      ),
    )
    test("200 response") {
      checkAll(HttpGen.method) { method =>
        val actual = status(method, !! / "HExitSuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      }
    } +
      test("500 response") {
        checkAll(methodGenWithoutHEAD) { method =>
          val actual = status(method, !! / "HExitFailure")
          assertZIO(actual)(equalTo(Status.InternalServerError))
        }
      } +
      test("404 response ") {
        checkAll(methodGenWithoutHEAD) { method =>
          val actual = status(method, !! / "A")
          assertZIO(actual)(equalTo(Status.NotFound))
        }
      }

  }

  def serverStartSpec = suite("ServerStartSpec") {
    test("desired port") {
      val port = 8088
      ZIO.scoped {
        (Server.port(port) ++ Server.app(Http.empty)).make.flatMap { start =>
          assertZIO(ZIO.attempt(start.port))(equalTo(port))
        }
      }
    } +
      test("available port") {
        ZIO.scoped {
          (Server.port(0) ++ Server.app(Http.empty)).make.flatMap { start =>
            assertZIO(ZIO.attempt(start.port))(not(equalTo(0)))
          }
        }
      }
  }

  override def spec =
    suite("Server") {
      app
        .as(
          List(serverStartSpec, staticAppSpec, nonZIOSpec, throwableAppSpec),
        )
    }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(30 seconds)

  def staticAppSpec    = suite("StaticAppSpec") {
    test("200 response") {
      val actual = status(path = !! / "success")
      assertZIO(actual)(equalTo(Status.Ok))
    } +
      test("500 response on failure") {
        val actual = status(path = !! / "failure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      } +
      test("500 response on die") {
        val actual = status(path = !! / "die")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      } +
      test("404 response") {
        val actual = status(path = !! / "random")
        assertZIO(actual)(equalTo(Status.NotFound))
      } +
      test("200 response with encoded path") {
        val actual = status(path = !! / "get%2Fsuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      } +
      test("Multiple 200 response") {
        for {
          data <- status(path = !! / "success").repeatN(1024)
        } yield assertTrue(data == Status.Ok)
      }
  }
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        status <- status(Method.GET, !! / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }
}
