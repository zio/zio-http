package zio.http.service

import zio.http.Middleware.cors
import zio.http._
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zio.http.middleware.Cors.CorsConfig
import zio.http.model.{Headers, Method, Request, Response, Status}
import zio.test.Assertion.{equalTo, not}
import zio.test.TestAspect.timeout
import zio.test.{Gen, TestEnvironment, assertTrue, assertZIO, checkAll}
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

  private val staticAppWithCors = Http.collectZIO[Request] { case Method.GET -> !! / "success-cors" =>
    ZIO.succeed(Response.ok.withVary("test1").withVary("test2"))
  } @@ cors(CorsConfig(allowedMethods = Some(Set(Method.GET, Method.POST))))

  private val app = serve { nonZIO ++ staticApp ++ staticAppWithCors }

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
        val actual = status(method, !! / "HExitSuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      }
    },
    test("500 response") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, !! / "HExitFailure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      }
    },
    test("404 response ") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, !! / "A")
        assertZIO(actual)(equalTo(Status.NotFound))
      }
    },
  )

  def serverStartSpec = suite("ServerStartSpec")(
    test("desired port") {
      val port = 8088
      ZIO.scoped {
        (Server.port(port) ++ Server.app(Http.empty)).make.flatMap { start =>
          assertZIO(ZIO.attempt(start.port))(equalTo(port))
        }
      }
    },
    test("available port") {
      ZIO.scoped {
        (Server.port(0) ++ Server.app(Http.empty)).make.flatMap { start =>
          assertZIO(ZIO.attempt(start.port))(not(equalTo(0)))
        }
      }
    },
  )

  override def spec =
    suite("Server") {
      app
        .as(
          List(serverStartSpec, staticAppSpec, nonZIOSpec, throwableAppSpec, multiHeadersSpec),
        )
    }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(30 seconds)

  def staticAppSpec    = suite("StaticAppSpec")(
    test("200 response") {
      val actual = status(path = !! / "success")
      assertZIO(actual)(equalTo(Status.Ok))
    },
    test("500 response on failure") {
      val actual = status(path = !! / "failure")
      assertZIO(actual)(equalTo(Status.InternalServerError))
    },
    test("500 response on die") {
      val actual = status(path = !! / "die")
      assertZIO(actual)(equalTo(Status.InternalServerError))
    },
    test("404 response") {
      val actual = status(path = !! / "random")
      assertZIO(actual)(equalTo(Status.NotFound))
    },
    test("200 response with encoded path") {
      val actual = status(path = !! / "get%2Fsuccess")
      assertZIO(actual)(equalTo(Status.Ok))
    },
    test("Multiple 200 response") {
      for {
        data <- status(path = !! / "success").repeatN(1024)
      } yield assertTrue(data == Status.Ok)
    },
  )
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        status <- status(Method.GET, !! / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }

  def multiHeadersSpec = suite("Multi headers spec")(
    test("Multiple headers should have the value combined in a single header") {
      for {
        result <- headers(Method.GET, !! / "success-cors")
      } yield {
        assertTrue(result.hasHeader(HeaderNames.vary)) &&
        assertTrue(result.vary.contains("test1,test2"))
      }
    },
    test("CORS headers should be properly encoded") {
      for {
        result <- headers(Method.GET, !! / "success-cors", Headers.origin("example.com"))
      } yield {
        assertTrue(result.hasHeader(HeaderNames.accessControlAllowMethods)) &&
        assertTrue(result.accessControlAllowMethods.contains("GET,POST"))
      }
    },
  )
}
