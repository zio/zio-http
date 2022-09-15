package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.http.internal.HttpRunnableSpec
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object StaticServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

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
    testM("200 response") {
      checkAllM(HttpGen.method) { method =>
        val actual = status(method, !! / "HExitSuccess")
        assertM(actual)(equalTo(Status.Ok))
      }
    } +
      testM("500 response") {
        checkAllM(methodGenWithoutHEAD) { method =>
          val actual = status(method, !! / "HExitFailure")
          assertM(actual)(equalTo(Status.InternalServerError))
        }
      } +
      testM("404 response ") {
        checkAllM(methodGenWithoutHEAD) { method =>
          val actual = status(method, !! / "A")
          assertM(actual)(equalTo(Status.NotFound))
        }
      }

  }

  def serverStartSpec = suite("ServerStartSpec") {
    testM("desired port") {
      val port = 8088
      (Server.port(port) ++ Server.app(Http.empty)).make.use { start =>
        assertM(ZIO.effect(start.port))(equalTo(port))
      }
    } +
      testM("available port") {
        (Server.port(0) ++ Server.app(Http.empty)).make.use { start =>
          assertM(ZIO.effect(start.port))(not(equalTo(0)))
        }
      }
  }

  override def spec =
    suiteM("Server") {
      app
        .as(
          List(serverStartSpec, staticAppSpec, nonZIOSpec, throwableAppSpec),
        )
        .useNow
    }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def staticAppSpec    = suite("StaticAppSpec") {
    testM("200 response") {
      val actual = status(path = !! / "success")
      assertM(actual)(equalTo(Status.Ok))
    } +
      testM("500 response on failure") {
        val actual = status(path = !! / "failure")
        assertM(actual)(equalTo(Status.InternalServerError))
      } +
      testM("500 response on die") {
        val actual = status(path = !! / "die")
        assertM(actual)(equalTo(Status.InternalServerError))
      } +
      testM("404 response") {
        val actual = status(path = !! / "random")
        assertM(actual)(equalTo(Status.NotFound))
      } +
      testM("200 response with encoded path") {
        val actual = status(path = !! / "get%2Fsuccess")
        assertM(actual)(equalTo(Status.Ok))
      } +
      testM("Multiple 200 response") {
        for {
          data <- status(path = !! / "success").repeatN(1024)
        } yield assertTrue(data == Status.Ok)
      }
  }
  def throwableAppSpec = suite("ThrowableAppSpec") {
    testM("Throw inside Handler") {
      for {
        status <- status(Method.GET, !! / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }
}
