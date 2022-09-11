package zio.http.service

import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec}
import zio.http.service.ServerSpec.requestBodySpec
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.assertZIO
import zio.{ZIO, durationInt}

object RequestStreamingServerSpec extends HttpRunnableSpec {
  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.enableObjectAggregator(-1)))

  /**
   * Generates a string of the provided length and char.
   */
  private def genString(size: Int, char: Char): String = {
    val buffer = new Array[Char](size)
    for (i <- 0 until size) buffer(i) = char
    new String(buffer)
  }

  val streamingServerSpec = suite("ServerStreamingSpec")(
    test("test unsafe large content") {
      val size    = 1024 * 1024
      val content = genString(size, '?')
      val app     = Http.fromFunctionZIO[Request] {
        _.body.asStream.runCount
          .map(bytesCount => Response.text(bytesCount.toString))
      }
      val res     = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(content))
      assertZIO(res)(equalTo(size.toString))
    },
    test("multiple body read") {
      val app = Http.collectZIO[Request] { case req =>
        for {
          _ <- req.body.asChunk
          _ <- req.body.asChunk
        } yield Response.ok
      }
      val res = app.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    },
  ) @@ timeout(10 seconds)

  override def spec =
    suite("RequestStreamingServerSpec") {
      suite("app with request streaming") {
        ZIO.scoped(appWithReqStreaming.as(List(requestBodySpec, streamingServerSpec)))
      }
    }.provideLayerShared(env) @@ timeout(10 seconds) @@ sequential

}
