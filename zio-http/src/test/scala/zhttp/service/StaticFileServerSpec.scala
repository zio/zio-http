package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.internal.HttpRunnableSpec.HttpIO
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

  def staticSpec1 = suite("Static File Server")(
    testM("should list contents of root directory") {
      val app                      = Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
      val res: HttpIO[Any, String] = app.requestBodyAsString()
      assertM(res)(containsString("<li><a href=\"TestFile2.txt\">TestFile2.txt</a></li>"))
    },
    testM("should show file contents") {
      val app = Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
      val res = app.requestBodyAsString(path = Path("/TestFile2.txt"))
      assertM(res)(equalTo("This is a test file for testing Static File Server."))
    },
    testM("should have content-type header \"text/plain\"") {
      val app = Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
      val res = app.request(path = Path("/TestFile2.txt")).map(_.getHeaderValue(HttpHeaderNames.CONTENT_TYPE))
      assertM(res)(isSome(equalTo("text/plain")))
    },
    testM("should respond with \"Not Found\"") {
      val app = Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
      val res = app.requestBodyAsString(path = Path("/NonExistentFile.txt"))
      assertM(res)(equalTo("The requested URI \"/NonExistentFile.txt\" was not found on this server\n"))
    },
    testM("should respond with status 405") {
      val app = Http.fromPath(Paths.get(getClass.getResource("/TestStatic/Folder2").getPath))
      val res = app.requestStatus(method = Method.POST, path = Path("/TextFile2.txt"))
      assertM(res)(equalTo(Status.METHOD_NOT_ALLOWED))
    },
    testM("should respond with \"Not Found\" if directory path does not exist") {
      val app = Http.fromPath(Paths.get(getClass.getResource("").getPath + "/NonExistentDir"))
      val res = app.requestBodyAsString()
      assertM(res)(equalTo("The requested URI \"/\" was not found on this server\n"))
    },
    testM("should respond with file contents if root path is a file") {
      val app = Http.fromPath(Paths.get(getClass.getResource("/TestFile.txt").getPath))
      val res = app.requestBodyAsString()
      assertM(res)(equalTo("abc\nfoo"))
    },
  )

  override def spec = suiteM("StaticFileServer") {
    serve(DynamicServer.app).as(List(staticSpec1)).useNow
  }.provideCustomLayerShared(env) @@ timeout(5 seconds)
}
