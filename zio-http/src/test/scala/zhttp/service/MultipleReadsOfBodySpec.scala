package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test.assertM

object MultipleReadsOfBodySpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ zhttp.service.server.ServerChannelFactory.nio ++ DynamicServer.live

  val simpleApp: HttpApp[Any, Nothing] = Http.collectZIO[Request] { case req @ Method.POST -> !! / "text" =>
    val reqBody = req.bodyAsString
    reqBody.map(input => Response.text(s"Hello World! $input")).catchAll { case err: Throwable =>
      ZIO.succeed(Response.text(s"Failed with ${err.getMessage}"))
    }
  }

  val logMiddleware =
    Middleware.interceptZIOPatch(req => ZIO.succeed(req)) { case (_, reqBody) =>
      for {
        _ <- reqBody.bodyAsString.orDie
      } yield Patch.setStatus(Status.Ok)
    }

  val app = serve(simpleApp @@ logMiddleware, Some(Server.enableObjectAggregator(Int.MaxValue)))

  override def spec = suiteM("Multiple reads") {
    app
      .as(
        List(multipleReads),
      )
      .useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def multipleReads = suite("Multiple reads with aggregator") {
    testM("successful test") {
      val actual =
        content(method = Method.POST, path = !! / "text", content = HttpData.fromString("some body content"))
      assertM(actual)(equalTo("Hello World! some body content"))
    }
  }
}
