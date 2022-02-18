package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.duration.durationInt
import zio.test.Assertion.{containsString, equalTo, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertM

import java.nio.file.Paths

object StaticFileServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

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

          testM("should respond with error") {
            val res = simpleDirectory.run().map(_.status)
            assertM(res)(equalTo(Status.INTERNAL_SERVER_ERROR))
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
      suite("fromPath") {
        testM("should list contents of root directory") {
          val res =
            Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath)).deploy.bodyAsString.run()
          assertM(res)(containsString("<li><a href=\"TestFile2.txt\">TestFile2.txt</a></li>"))
        } +
          testM("should show file contents") {
            val res = Http
              .fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
              .deploy
              .bodyAsString
              .run(path = Path("/TestFile2.txt"))
            assertM(res)(equalTo("This is a test file for testing Static File Server."))
          } +
          testM("should have content-type header \"text/plain\"") {
            val res = Http
              .fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
              .deploy
              .headerValue(HttpHeaderNames.CONTENT_TYPE)
              .run(path = Path("/TestFile2.txt"))
            assertM(res)(isSome(equalTo("text/plain")))
          } +
          testM("should respond with `empty`") {
            val res = Http
              .fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
              .deploy
              .bodyAsString
              .run(path = Path("/NonExistentFile.txt"))
            assertM(res)(equalTo(""))
          } +
          testM("should respond with status 405") {
            val res = Http
              .fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
              .deploy
              .status
              .run(method = Method.POST)
            assertM(res)(equalTo(Status.METHOD_NOT_ALLOWED))
          } +
          testM("should respond with `empty` if directory path does not exist") {
            val res =
              Http
                .fromPath(Paths.get(getClass.getResource("").getPath + "/NonExistentDir"))
                .deploy
                .bodyAsString
                .run()
            assertM(res)(equalTo(""))
          } +
          testM("should respond with file contents if root path is a file") {
            val res =
              Http.fromPath(Paths.get(getClass.getResource("/TestFile.txt").getPath)).deploy.bodyAsString.run()
            assertM(res)(equalTo("abc\nfoo"))
          }
      }
  }

  override def spec = suiteM("StaticFileServer") {
    serve(DynamicServer.app).as(List(staticSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(5 seconds)
}
