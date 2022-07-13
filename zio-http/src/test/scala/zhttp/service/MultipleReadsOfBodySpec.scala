package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zio._
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test.{Spec, TestEnvironment, assertZIO}

object MultipleReadsOfBodySpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ zhttp.service.server.ServerChannelFactory.nio ++ DynamicServer.live

  val simpleApp: HttpApp[Any, Nothing] = Http.collectZIO[Request] { case req @ Method.POST -> !! / "text" =>
    req.bodyAsString.map(input => Response.text(s"Hello World! $input")).catchAll { case err: Throwable =>
      ZIO.succeed(Response.text(s"Failed with ${err.getMessage}"))
    }
  }

  private val logMiddleware =
    Middleware.interceptZIOPatch(req => ZIO.succeed(req)) { case (_, reqBody) =>
      for {
        _ <- reqBody.bodyAsString.orDie
      } yield Patch.setStatus(Status.Ok)
    }

  val app        = serve(simpleApp @@ logMiddleware, Some(Server.enableObjectAggregator(Int.MaxValue)))
  val appChunked = serve(simpleApp @@ logMiddleware)

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Content read")(withAggregator, withOutAggregator)

  val withAggregator = suite("multiple body reads") {
    ZIO.scoped(app.as(List(multiReadsWithAggregator)))
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  val withOutAggregator = suite("multiple body reads") {
    ZIO.scoped(appChunked.as(List(multiReadsWithoutAggregator)))
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  val multiReadsWithAggregator    = suite("Multiple body reads with aggregator") {
    test("should properly read the body") {
      val actual = content(path = !! / "text", content = HttpData.fromString("some content"))
      assertZIO(actual)(equalTo("Hello World! some content"))
    }
  }
  val multiReadsWithoutAggregator = suite("Multiple body reads without aggregator") {
    test("should throw exception") {
      val actual: ZIO[EventLoopGroup with ChannelFactory with DynamicServer, Throwable, String] =
        content(path = !! / "text", content = HttpData.fromString("some content"))
      assertZIO(actual.exitCode)(equalTo(ExitCode.success))
    }
  }
}
