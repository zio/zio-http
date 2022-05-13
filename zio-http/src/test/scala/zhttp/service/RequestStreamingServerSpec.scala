package zhttp.service
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ServerSpec.{requestBodySpec, unsafeContentSpec}
import zio.duration.durationInt
import zio.stream.ZSink
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.assertM

import java.io.File

object RequestStreamingServerSpec extends HttpRunnableSpec {
  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ zhttp.service.server.ServerChannelFactory.nio ++ DynamicServer.live

  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.enableObjectAggregator(-1)))

  def largeContentSpec = suite("ServerStreamingSpec") {
    testM("test unsafe large content") {
      val app  = Http.collectZIO[Request] { case req @ Method.POST -> !! / "store" =>
        for {
          bytesCount <- req.bodyAsStream.run(ZSink.count)
        } yield Response.text(bytesCount.toString)
      }
      val file = new File(getClass.getResource("/1M.img").getPath)
      assertM(
        app.deploy.bodyAsString.run(
          path = !! / "store",
          method = Method.POST,
          content = HttpData.fromFile(file),
        ),
      )(
        equalTo("1048576"),
      )
    }
  }

  override def spec =
    suite("Server") {
      val spec =
        requestBodySpec + unsafeContentSpec + largeContentSpec
      suiteM("app with request streaming") { appWithReqStreaming.as(List(spec)).useNow }
    }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ sequential

}
