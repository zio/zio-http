package zhttp.service
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ServerSpec.{requestBodySpec, unsafeContentSpec}
import zhttp.service.server.ServerChannelFactory
import zio.duration.durationInt
import zio.test.TestAspect.{sequential, timeout}

object RequestStreamingServerSpec extends HttpRunnableSpec {
  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.enableObjectAggregator(-1)))

  override def spec =
    suite("Server") {
      val spec =
        requestBodySpec + unsafeContentSpec
      suiteM("app with request streaming") { appWithReqStreaming.as(List(spec)).useNow }
    }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ sequential

}
