package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpGen, HttpRunnableSpec, testClient}
import zhttp.service.ChannelModel.ChannelType
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Scope, ZIO, durationInt}

object StaticServerSpec extends HttpRunnableSpec {

  private val env = DynamicServer.live ++ Scope.default

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

  private val methodGenWithoutHEAD: Gen[Any, Method] = Gen.fromIterable(
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

  def nonZIOSpec = suite("NonZIOSpec")(
    test("200 response") {
      checkAll(HttpGen.method) { method =>
        val actual = testClient
          .flatMap(client => status(method, !! / "HExitSuccess", client))
        assertZIO(actual)(equalTo(Status.Ok))
      }
    },
    test("500 response") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = testClient
          .flatMap(client => status(method, !! / "HExitFailure", client))
        assertZIO(actual)(equalTo(Status.InternalServerError))
      }
    },
    test("404 response ") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = testClient
          .flatMap(client => status(method, !! / "A", client))
        assertZIO(actual)(equalTo(Status.NotFound))
      }
    },
  )

  def serverStartSpec = suite("ServerStartSpec")(
    test("desired port") {
      val port = 8088
      ZIO.scoped {
        (Server.port(port) ++ Server.app(Http.empty) ++ Server.serverChannelType(ChannelType.NIO)).make.flatMap {
          start =>
            assertZIO(ZIO.attempt(start.port))(equalTo(port))
        }
      }
    },
    test("available port") {
      ZIO.scoped {
        (Server.port(0) ++ Server.app(Http.empty) ++ Server.serverChannelType(ChannelType.NIO)).make.flatMap { start =>
          assertZIO(ZIO.attempt(start.port))(not(equalTo(0)))
        }
      }
    },
  )

  override def spec =
    suite("Server") {
      app
        .as(
          List(serverStartSpec, staticAppSpec, nonZIOSpec, throwableAppSpec),
        )
    }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(30 seconds)

  def staticAppSpec    = suite("StaticAppSpec")(
    test("200 response") {
      val actual = testClient
        .flatMap(client => status(path = !! / "success", client = client))
      assertZIO(actual)(equalTo(Status.Ok))
    },
    test("500 response on failure") {
      val actual = testClient
        .flatMap(client => status(path = !! / "failure", client = client))
      assertZIO(actual)(equalTo(Status.InternalServerError))
    },
    test("500 response on die") {
      val actual = testClient
        .flatMap(client => status(path = !! / "die", client = client))
      assertZIO(actual)(equalTo(Status.InternalServerError))
    },
    test("404 response") {
      val actual = testClient
        .flatMap(client => status(path = !! / "random", client = client))
      assertZIO(actual)(equalTo(Status.NotFound))
    },
    test("200 response with encoded path") {
      val actual = testClient
        .flatMap(client => status(path = !! / "get%2Fsuccess", client = client))
      assertZIO(actual)(equalTo(Status.Ok))
    },
    test("Multiple 200 response") {
      for {
        client <- testClient
        data   <- status(path = !! / "success", client = client).repeatN(1024)
      } yield assertTrue(data == Status.Ok)
    },
  )
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        client <- testClient
        status <- status(Method.GET, !! / "throwable", client = client)
      } yield assertTrue(status == Status.InternalServerError)
    }
  }
}
