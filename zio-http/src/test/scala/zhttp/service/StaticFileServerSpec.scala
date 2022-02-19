package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion.{containsString, equalTo, isSome}
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
          val res = fileOk.run().map(_.status)
          assertM(res)(equalTo(Status.OK))
        } +
          testM("should have content-length") {
            val res = fileOk.run().map(_.contentLength)
            assertM(res)(isSome(equalTo(7L)))
          } +
          testM("should have content") {
            val res = fileOk.run().flatMap(_.bodyAsString)
            assertM(res)(equalTo("abc\nfoo"))
          } +
          testM("should have content-type") {
            val res = fileOk.run().map(_.mediaType)
            assertM(res)(isSome(equalTo(MediaType.text.plain)))
          } +
          testM("should respond with empty") {
            val res = fileNotFound.run().map(_.status)
            assertM(res)(equalTo(Status.NOT_FOUND))
          }
      } +
        suite("directory") {
          val simpleDirectory = Http.fromResource("/TestStatic").deploy
          val fileInDirectory = Http.fromResource("/TestStatic/Folder2/TestFile2.txt").deploy
          val fileNotFound    = Http.fromResource("/TestStatic/Folder2/Nothing").deploy

          testM("should respond ok") {
            val res = simpleDirectory.run().map(_.status)
            assertM(res)(equalTo(Status.OK))
          } +
            testM("should contain file listing") {
              val res = simpleDirectory.run().flatMap(_.bodyAsString)
              assertM(res)(containsString("<li><a href=\"TestFile1.txt\">TestFile1.txt</a></li>"))
            } +
            testM("should have 200 status code") {
              val res = fileInDirectory.run().map(_.status)
              assertM(res)(equalTo(Status.OK))
            } +
            testM("should respond not found") {
              val res = fileNotFound.run().map(_.status)
              assertM(res)(equalTo(Status.NOT_FOUND))
            }
        }
    } +
      suite("fromFile") {
        suite("failure on construction") {
          testM("should respond with 500") {
            val res = Http.fromFile(throw new Error("Wut happened?")).deploy.run().map(_.status)
            assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
          }
        } +
          suite("invalid file") {
            testM("should respond with 500") {
              final class BadFile(name: String) extends File(name) {
                override def length: Long    = throw new Error("Haha")
                override def isFile: Boolean = true
              }
              val res = Http.fromFile(new BadFile("Length Failure")).deploy.run().map(_.status)
              assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
            }
          }
      }
  }

}
