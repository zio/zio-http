package zhttp.service
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ServerSpec.{requestBodySpec, unsafeContentSpec}
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.{Gen, assertM, checkM}

object RequestStreamingServerSpec extends HttpRunnableSpec {
  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ zhttp.service.server.ServerChannelFactory.nio ++ DynamicServer.live

  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.enableObjectAggregator(-1)))

  def largeContentSpec = suite("ServerStreamingSpec") {
    testM("test unsafe large content") {
      val size = 1024 * 1024
      checkM(Gen.stringBounded(size, size)(Gen.alphaNumericChar)) { content =>
        val app = Http.fromFunctionZIO[Request] {
          _.bodyAsStream.runCount.map(bytesCount => Response.text(bytesCount.toString))
        }

        val res = app.deploy.bodyAsString.run(content = HttpData.fromString(content))

        assertM(res)(equalTo(size.toString))
      }
    }
  }

  override def spec =
    suite("Server") {
      val spec =
        requestBodySpec + unsafeContentSpec + largeContentSpec
      suiteM("app with request streaming") { appWithReqStreaming.as(List(spec)).useNow }
    }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ sequential

}
