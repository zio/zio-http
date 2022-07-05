package zhttp.service

import zhttp.http.{Http, HttpData, Request, Response}
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zio.stream.ZStream
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.{TestEnvironment, assertZIO}
import zio.{ZIO, durationInt}

object TestSpec extends HttpRunnableSpec {

  private val MaxSize = 1024 * 10

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  private val app =
    serve(DynamicServer.app, Some(Server.enableObjectAggregator(MaxSize)))

  private val bodySpec =
    test("data") {
      val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
      val app        = Http.collect[Request] { case req => Response(data = req.data) }
      val res        = app.deploy.bodyAsByteBuf.map(_.readableBytes()).run(content = HttpData.fromStream(dataStream))
      assertZIO(res)(equalTo(MaxSize))
    }
//    +
//      test("POST Request stream") {
//        val app: Http[Any, Throwable, Request, Response] = Http.collect[Request] { case req =>
//          Response(data = HttpData.fromStream(req.bodyAsStream))
//        }
//        check(Gen.alphaNumericString) { c =>
//          assertZIO(app.deploy.bodyAsString.run(path = !!, method = Method.POST, content = HttpData.fromString(c)))(
//            equalTo(c),
//          )
//        }
//      }

  override def spec = {
    suite("app without request streaming") { ZIO.logSpan("test") { ZIO.scoped(app.as(List(bodySpec))) } }

  }.provideSomeLayerShared[TestEnvironment](env) @@ timeout(20 seconds) @@ sequential

}
