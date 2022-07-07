package zhttp.service

import zhttp.http.{Http, HttpData, Request, Response}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zio.stream.ZStream
import zio.test.Assertion.{containsString, equalTo}
import zio.test.TestAspect.{sequential, timeout}
import zio.test.{TestEnvironment, assertZIO}
import zio.{ZIO, durationInt}

object TestSpec extends HttpRunnableSpec {

  private val MaxSize = 1024 * 10
  private val env     =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  private val app =
    serve(DynamicServer.app, Some(Server.requestDecompression(true) ++ Server.enableObjectAggregator(MaxSize)))

  val echoSuite = suite("echo content") {

    test("data") {
      val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
      val app        = Http.collect[Request] { case req => Response(data = req.data) }
      val res        = app.deploy.bodyAsByteBuf.map(_.readableBytes()).run(content = HttpData.fromStream(dataStream))
      assertZIO(res)(equalTo(MaxSize))
    }
  }

  def requestBodySpec = suite("RequestBodySpec") {
    test("content is set") {
      val res = Http.text("ABC").deploy.bodyAsString.run()
      assertZIO(res)(containsString("ABC"))
    }
  }

  override def spec = suite("Server") {
    val spec = echoSuite
    suite("app without request streaming") { ZIO.scoped(app.as(List(spec))) }
    // suite("app with request streaming") { ZIO.scoped(appWithReqStreaming.as(List(spec))) }
  }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(90 seconds) @@ sequential

}
