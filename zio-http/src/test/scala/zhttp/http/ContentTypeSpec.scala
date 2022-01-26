package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test.assertM

import java.io.File

object ContentTypeSpec extends HttpRunnableSpec {

  private val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  val contentSpec = suite("Content type header on file response") {
    testM("mp4") {
      val file = new File(getClass.getResource("/TestFile2.mp4").getPath)
      val res  =
        Http
          .fromFile(file)
          .deploy
          .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
          .run()
          .map(_.getOrElse("Content type header not found."))
      assertM(res)(equalTo("video/mp4"))
    } +
      testM("js") {
        val file = new File(getClass.getResource("/TestFile3.js").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("application/javascript"))
      } +
      testM("no extension") {
        val file = new File(getClass.getResource("/TestFile4").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("Content type header not found."))
      } +
      testM("css") {
        val file = new File(getClass.getResource("/TestFile5.css").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("text/css"))
      } +
      testM("mp3") {
        val file = new File(getClass.getResource("/TestFile6.mp3").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("audio/mpeg"))
      } +
      testM("unidentified extension") {
        val file = new File(getClass.getResource("/truststore.jks").getPath)
        val res  =
          Http
            .fromFile(file)
            .deploy
            .getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
            .run()
            .map(_.getOrElse("Content type header not found."))
        assertM(res)(equalTo("Content type header not found."))
      }
  }

  override def spec = {
    suiteM("Content-type") {
      serve(DynamicServer.app).as(List(contentSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds)
  }
}
