package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion.{equalTo, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertM

import java.io.File

object StaticFileServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  override def spec = suiteM("StaticFileServer") {
    serve(DynamicServer.app).as(List(staticSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(5 seconds)

  private def staticSpec = suite("Static RandomAccessFile Server") {
    suite("fromResource") {
      suite("file") {
        val fileOk       = Http.fromResource("/TestFile.txt").deploy
        val fileNotFound = Http.fromResource("/Nothing").deploy
        testM("should have 200 status code") {
          val res = fileOk.runApp().map(_.status)
          assertM(res)(equalTo(Status.OK))
        } +
          testM("should have content-length") {
            val res = fileOk.runApp().map(_.contentLength)
            assertM(res)(isSome(equalTo(7L)))
          } +
          testM("should have content") {
            val res = fileOk.runApp().flatMap(_.bodyAsString)
            assertM(res)(equalTo("abc\nfoo"))
          } +
          testM("should have content-type") {
            val res = fileOk.runApp().map(_.mediaType)
            assertM(res)(isSome(equalTo(MediaType.text.plain)))
          } +
          testM("should respond with empty") {
            val res = fileNotFound.runApp().map(_.status)
            assertM(res)(equalTo(Status.NOT_FOUND))
          }
      }
    } +
      suite("fromFile") {
        suite("failure on construction") {
          testM("should respond with 500") {
            val res = Http.fromFile(throw new Error("Wut happened?")).deploy.runApp().map(_.status)
            assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
          }
        } +
          suite("invalid file") {
            testM("should respond with 500") {
              final class BadFile(name: String) extends File(name) {
                override def length: Long    = throw new Error("Haha")
                override def isFile: Boolean = true
              }
              val res = Http.fromFile(new BadFile("Length Failure")).deploy.runApp().map(_.status)
              assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
            }
          }
      }
  }

}
