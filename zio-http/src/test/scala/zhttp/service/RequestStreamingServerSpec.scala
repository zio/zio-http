package zhttp.service
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.ServerSpec.requestBodySpec
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test._
import zio.{Scope, durationInt}

object RequestStreamingServerSpec extends HttpRunnableSpec {
  private val env = DynamicServer.live ++ Scope.default

  private val appWithReqStreaming = serve(DynamicServer.app, Some(Server.enableObjectAggregator(-1)))

  /**
   * Generates a string of the provided length and char.
   */
  private def genString(size: Int, char: Char): String = {
    val buffer = new Array[Char](size)
    for (i <- 0 until size) buffer(i) = char
    new String(buffer)
  }

  def largeContentSpec = suite("ServerStreamingSpec") {
    test("test unsafe large content") {
      val size    = 1024 * 1024
      val content = genString(size, '?')

      val app = Http.fromFunctionZIO[Request] {
        _.bodyAsStream.runCount.map(bytesCount => {
          Response.text(bytesCount.toString)
        })
      }

      val res = app.deploy.bodyAsString.run(content = HttpData.fromString(content))

      assertZIO(res)(equalTo(size.toString))

    }
  }

  override def spec =
    suite("RequestStreamingServerSpec") {
      val spec = requestBodySpec + largeContentSpec
      suite("app with request streaming") { appWithReqStreaming.as(List(spec)) }
    }.provideCustomLayerShared(env) @@ timeout(10 seconds) @@ sequential

}
