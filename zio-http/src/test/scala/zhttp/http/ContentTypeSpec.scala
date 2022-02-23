package zhttp.http

import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertM

object ContentTypeSpec extends HttpRunnableSpec {

  val contentSpec = suite("Content type header on file response") {
    test("mp4") {
      val res = Http.fromResource("/TestFile2.mp4").deploy.contentType.run()
      assertM(res)(isSome(equalTo("video/mp4")))
    } +
      test("js") {
        val res = Http.fromResource("/TestFile3.js").deploy.contentType.run()
        assertM(res)(isSome(equalTo("application/javascript")))
      } +
      test("no extension") {
        val res = Http.fromResource("/TestFile4").deploy.contentType.run()
        assertM(res)(isNone)
      } +
      test("css") {
        val res = Http.fromResource("/TestFile5.css").deploy.contentType.run()
        assertM(res)(isSome(equalTo("text/css")))
      } +
      test("mp3") {
        val res = Http.fromResource("/TestFile6.mp3").deploy.contentType.run()
        assertM(res)(isSome(equalTo("audio/mpeg")))
      } +
      test("unidentified extension") {
        val res = Http.fromResource("/truststore.jks").deploy.contentType.run()
        assertM(res)(isNone)
      } +
      test("already set content-type") {
        val expected = MediaType.application.`json`
        val res      = Http.fromResource("/TestFile6.mp3").map(_.withMediaType(expected)).deploy.contentType.run()
        assertM(res)(isSome(equalTo(expected.fullType)))
      }
  }

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  override def spec = {
    suite("Content-type") {
      serve(DynamicServer.app).as(List(contentSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds)
  }
}
