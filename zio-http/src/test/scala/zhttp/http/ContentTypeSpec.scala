package zhttp.http

import zhttp.internal.DynamicServer
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.duration.durationInt
import zio.http.internal.HttpRunnableSpec
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertM

object ContentTypeSpec extends HttpRunnableSpec {

  val contentSpec = suite("Content type header on file response") {
    testM("mp4") {
      val res = Http.fromResource("TestFile2.mp4").deploy.contentType.run()
      assertM(res)(isSome(equalTo("video/mp4")))
    } +
      testM("js") {
        val res = Http.fromResource("TestFile3.js").deploy.contentType.run()
        assertM(res)(isSome(equalTo("application/javascript")))
      } +
      testM("no extension") {
        val res = Http.fromResource("TestFile4").deploy.contentType.run()
        assertM(res)(isNone)
      } +
      testM("css") {
        val res = Http.fromResource("TestFile5.css").deploy.contentType.run()
        assertM(res)(isSome(equalTo("text/css")))
      } +
      testM("mp3") {
        val res = Http.fromResource("TestFile6.mp3").deploy.contentType.run()
        assertM(res)(isSome(equalTo("audio/mpeg")))
      } +
      testM("unidentified extension") {
        val res = Http.fromResource("truststore.jks").deploy.contentType.run()
        assertM(res)(isNone)
      } +
      testM("already set content-type") {
        val expected = MediaType.application.`json`
        val res      = Http.fromResource("TestFile6.mp3").map(_.withMediaType(expected)).deploy.contentType.run()
        assertM(res)(isSome(equalTo(expected.fullType)))
      }
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  override def spec = {
    suiteM("Content-type") {
      serve(DynamicServer.app).as(List(contentSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds)
  }
}
